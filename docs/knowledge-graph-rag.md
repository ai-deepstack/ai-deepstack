# 知识库图谱增强（GraphRAG）方案

按终态一次性交付，不分期。下列能力同一次实现、同一验收标准。

> **落地说明**：实现总结、启用步骤与排查见 [`knowledge-graph-rag-summary.md`](knowledge-graph-rag-summary.md)。

## 背景

当前知识库已具备：

- 文档解析 → 切片 → embedding → `knowledge_chunk`（pgvector）
- 混合召回（向量 + 全文 RRF，串行）
- CHAT 注入 system prompt；GRAPH 经 `rag-node` → `KnowledgePort`

`knowledge_base.enable_graph` 仍为预留。平台已有 `GraphStore`（AGE）与长期记忆图同步，**未接入知识库**。

本方案：入库用大模型抽实体/关系写图；召回在授权知识库内做向量 + 全文并发 + 图谱扩边；控制台提供知识图谱只读展示。

## 目标（终态）

1. 落地 `enable_graph`：建图 + 召回扩边 + 控制台展示。
2. 实体/关系由 **CHAT 大模型** JSON 抽取写入图。
3. 智能体绑定哪些库，向量/全文/图谱都只在这些库内进行。
4. 向量 + 全文并发；图谱在 hybrid 种子之后对多种子并发 expand；多库召回并行（限流）。
5. 图谱不可用或关闭时，RAG 与现网一致；写图失败不回滚向量。
6. 控制台知识库图谱页（Vue Flow 只读）：按库/按文档浏览、懒加载 expand、图例与属性面板。
7. 按库可配抽取模型；写图失败可重试；hops / 批大小 / 线程池可配。

非目标：用图替代 pgvector 主召回；与长期记忆共用同一 scope；前端手工改图。

## 范围与隔离

### 授权范围

- 可用库：`ai_agent_knowledge_base` 或请求覆盖的 `knowledgeBaseCodes`。
- 对每个 `baseCode`：该库内完成「向量 ∥ 全文 → RRF →（可选）图扩边」。
- 不跨库 hop，不读未授权库的图。

### 图隔离

| 用途 | scope |
| --- | --- |
| 知识库 | `kb:{baseCode}` |
| 长期记忆 | `mem_{user}_{agent}`（既有，不变） |

## 图模型

### 节点

| Label | key | 主要属性 |
| --- | --- | --- |
| Document | `doc_{documentId}` | `documentId`, `title`, `baseCode` |
| Chunk | `chk_{chunkId}` | `chunkId`, `documentId`, `preview` |
| Entity | `ent_{normalizedName}` | `name`, `type` |

### 边

| 类型 | 含义 |
| --- | --- |
| `CONTAINS` | Document → Chunk |
| `MENTIONS` | Chunk → Entity |
| `RELATED_TO` | Entity → Entity（模型关系；缺省类型时用此） |
| `ABOUT` | Document → Entity（文档级主题实体） |

删文档 / 重嵌：`deleteByDocument(scope, documentId)` 后重建。  
删库：`deleteScope(kb:{baseCode})`。

## 建图：大模型抽取

### 时机

`DocumentProcessorImpl`：chunk 落库且 `embed_status=EMBEDDED` 后异步写图。

```
解析 → 切片 → embedding 落库 → [enable_graph 且 GraphStore ready] → LLM 抽实体/关系 → upsert 图
```

### 模型

- 用 **CHAT** 模型，不用 embedding。
- 解析顺序：`knowledge_base.graph_model_code` → `deepstack.knowledge.graph.chat-model-code` → 平台第一条启用 CHAT → 否则 `graph_status=失败` 并跳过。

### 输入输出

按 chunk 批处理（`max-chunks-per-extract`）；同文档合并同名实体。模型只输出 JSON：

```json
{
  "entities": [{ "name": "弹簧", "type": "Concept" }],
  "relations": [{ "from": "弹簧", "to": "刚度", "type": "RELATED_TO" }]
}
```

`type` 枚举：Person / Org / Concept / Event / Other。解析失败记文档失败状态，**不回滚**向量；提供按文档重试 API。

### 写入步骤

1. `ensureScope(kb:{baseCode})`
2. upsert Document、Chunk，`CONTAINS`
3. upsert Entity，`MENTIONS` + 关系边
4. 高频实体可选 `ABOUT`

### 文档状态字段

| 字段 | 含义 |
| --- | --- |
| `graph_status` | 0 未处理 / 1 写入中 / 2 已写入 / 3 失败 / 4 跳过 |
| `graph_error` | 失败原因（截断） |
| `graph_model_code` | 可选，库级抽取模型（也可只放在 `knowledge_base`） |

`knowledge_base` 增加：`enable_graph`、`graph_model_code`。  
`reembed`：清 chunk → embed → `deleteByDocument` → 再抽图。  
重试：`PUT /api/kb/docs/{id}/regraph`（仅重跑写图，不重 embed）。

## 召回：并发模型（细规）

三路能力：

| 路 | 数据面 | 角色 |
| --- | --- | --- |
| 向量 | pgvector | 语义种子 |
| 全文 | `content_tsv` | 关键词种子 |
| 图谱 | AGE `kb:{baseCode}` | 种子扩边补召回 |

### 为何不是三路同时起跑

图谱边挂在 Chunk / Entity 上，**没有 hybrid 种子就没有 expand 起点**。因此：

- **阶段 A（并发）**：向量 ∥ 全文  
- **阶段 B（依赖 A，内部并发）**：对 seeds 并发 expand  

不是「三路 CompletableFuture 一起 allOf」。

### 线程与资源

| 项 | 约定 |
| --- | --- |
| 线程池 | 独立 `knowledgeRecallExecutor`（库内向量/全文/expand）+ `knowledgeMultiKbExecutor`（多库编排，防同池嵌套死锁） |
| 单库内阶段 A | 2 个任务：vectorSql、fullTextSql |
| 单库内阶段 B | 每个 seed 一个 expand 任务，`max-expand-concurrency` 限制（默认 min(seeds, 8)） |
| 多库 | 授权列表上每个库一个 `retrieveOneKb` 任务，`max-kb-concurrency`（默认 4，上限 8） |
| 超时 | 单路 SQL / 单次 expand 可配；多库总超时 = `timeout-ms × ceil(库数 / 并发)`，共用一个 deadline，超时该库空结果，不拖垮整次对话 |
| 失败隔离 | 单路异常 → 该路空列表 + warn；另一路仍参与 RRF；expand 单种子失败跳过 |

查询句 **embedding 必须在阶段 A 的向量路之前完成**（串行前置）；全文路不依赖 embedding，可与 embedding 并行启动，但向量 SQL 要等 embed 结果——更干净的写法是：

```
embedFuture = async embed(query)          // 可与「准备」重叠
textFuture  = async searchByFullText(...) // 立刻开
vector = await embedFuture
vectorFuture = async searchByVector(vector, ...)
seeds = rrf(await vectorFuture, await textFuture, topK)
```

即：**全文与 embedding 并行；向量 SQL 与全文的后半段并行**（若全文已先完成则只等向量）。

### 单库时序

```
query
  │
  ├──────────────────────────────► fullTextSql ────────┐
  │                                                      │
  └──► embed(query) ──► vectorSql ───────────────────────┤
                                                         ▼
                                              RRF(vectorHits, textHits) → seeds[topK]
                                                         │
                              enable_graph && ready?     │
                                    no ──────────────────┴──► return seeds
                                    yes
                                                         │
                    ┌── expand(seed1) ──┐
                    ├── expand(seed2) ──┼─ (信号量限流) ─► 收集 chunkId
                    └── expand(seedN) ──┘         │
                                                  ▼
                                         批量回表补文本 → 降权并入 → 截断 → return
```

### 多库时序

```
kbCodes = 授权列表（有序，保留优先级语义）
futures = kbCodes.map(code -> async retrieveOneKb(code))  // 最多 max-kb-concurrency 同时跑
parts = await all（保序：按 kbCodes 原序拼接各库文本块）
return join(parts)
```

库与库之间并行；库内仍是「阶段 A → 阶段 B」。拼接时**按绑定 priority / 列表顺序**输出，避免并行打乱「知识库 A 在前」的提示词结构。

### 融合规则

**阶段 A（RRF）** — 仅向量与全文：

\[
\mathrm{score}(id)=\sum_{r\in\{\mathrm{vec},\mathrm{text}\}}\frac{1}{K+\mathrm{rank}_r(id)}
\]

默认 `K=60`（与现 `RRF_K` 一致）。每路先召回 `recallSize = max(topK*3, 10)`，再取 RRF topK 为 seeds。

**阶段 B（图扩展）**：

- expand：`hops`（默认 2），`edgeTypes = [MENTIONS, RELATED_TO, CONTAINS, ABOUT]`，`limit` 每种子上限可配。
- 从邻居节点取 `chunkId` / `documentId`，去掉已在 seeds 中的 id，批量 `selectBatchIds` 取 `KnowledgeChunk`。
- 扩展块基础分：`graphScore = 0.7 / (1 + hopDistance)`（同 hop 再按出现次数微调可选）。
- 最终列表：seeds（RRF 分）∪ extras（graph 分），按分排序，截断为 `topK + graphExtra`（如 `graphExtra=topK`，默认总上限 `2*topK`），避免提示词过长。

未开图：只返回 RRF topK，行为与现网一致（但向量/全文改为并发）。

### 实现落点（代码结构）

```text
KnowledgeChunkServiceImpl
  hybridSearch(...)           // 阶段 A 并发 + RRF；签名可保留
  hybridSearchWithGraph(...)  // 或内部根据 kb.enableGraph 走阶段 B

KnowledgePortAdapter.retrieve(baseCode, query, topK, threshold)
  → getByBaseCode → hybrid(+graph) → List<String> contents

ChatServiceImpl
  → 只调 KnowledgePort（或多库编排放在 Port/Facade）
  → 多库并行在 KnowledgeRetrieveFacade.retrieveAll(kbCodes, ...)
```

`CompletableFuture` 一律用 `knowledgeRecallExecutor`，禁止 `ForkJoinPool.commonPool()` 跑阻塞 JDBC。

示例骨架：

```java
public List<KnowledgeChunk> recallOneKb(KnowledgeBase kb, String query, int topK, Double threshold) {
    int recallSize = Math.max(topK * 3, 10);
    CompletableFuture<List<KnowledgeChunk>> textF = CompletableFuture.supplyAsync(
            () -> mapper.searchByFullText(kb.getId(), query, recallSize), recallExecutor);

    String vector = embedToVectorString(kb.getEmbeddingModelCode(), query); // 与 textF 重叠：可再包一层
    CompletableFuture<List<KnowledgeChunk>> vecF = CompletableFuture.supplyAsync(
            () -> mapper.searchByVectorWithThreshold(kb.getId(), vector, recallSize, threshold),
            recallExecutor);

    List<KnowledgeChunk> seeds = rrf(vecF.join(), textF.join(), topK);

    if (!isGraphEnabled(kb) || !graphReady()) {
        return seeds;
    }
    return mergeGraphExpand(kb.getBaseCode(), seeds, topK);
}
```

更彻底的并发是把 `embed` 也 `supplyAsync`，与 `textF` 同时启动，再 `thenCompose` 出 `vecF`：

```java
CompletableFuture<List<KnowledgeChunk>> textF = supplyAsync(() -> fullText(...), ex);
CompletableFuture<List<KnowledgeChunk>> vecF = supplyAsync(() -> embed(query), ex)
        .thenApplyAsync(v -> vectorSearch(v, ...), ex);
List<KnowledgeChunk> seeds = rrf(vecF.join(), textF.join(), topK);
```

多库：

```java
Semaphore lim = new Semaphore(maxKbConcurrency);
List<CompletableFuture<KbPart>> fs = new ArrayList<>();
for (String code : kbCodes) {
    fs.add(supplyAsync(() -> {
        lim.acquireUninterruptibly();
        try { return recallOneKb(code, ...); }
        finally { lim.release(); }
    }, ex));
}
// 按 fs 与 kbCodes 下标顺序 join，再拼接
```

### 统一入口

- CHAT、`rag-node` **只走 `KnowledgePort` / Facade**，禁止再直接调 `hybridSearch` 分叉。
- `rag-node` 不增加「是否用图」属性，跟库的 `enable_graph`。

## 前端：知识图谱展示

与智能体编排画布（`GraphEditor`）分离：本页是 Document / Chunk / Entity 知识图。

### 入口与交互

- 路由：`/kb/:baseCode/graph`（知识库页入口按钮「查看图谱」）。
- `enable_graph=0` 或 `/api/graph/info` 未就绪：说明文案，不渲染空画布。
- 只读：缩放、拖拽、图例分色、点选看属性。
- 按 `documentId` 过滤；默认 `hops=1`、`limit=80`；选中节点可「展开邻居」懒加载。
- 不提供手工增删边。

### 技术

复用 **Vue Flow**。节点多时必须懒加载 + limit，禁止一次拉全库。

### API

| 接口 | 说明 |
| --- | --- |
| `GET /api/kb/{baseCode}/graph/summary` | 就绪状态、节点/边约数、最近写图时间 |
| `GET /api/kb/{baseCode}/graph/view` | `{ nodes, edges }`；参数 `documentId?`、`startKey?`、`hops`、`limit` |
| `PUT /api/kb/docs/{id}/regraph` | 写图重试 |
| GraphStore expand | 返回节点**且**边（或 view 内组装），否则前端无法连线 |

scope 由服务端固定为 `kb:{baseCode}`，前端不传任意 scope。

## 开关与配置

全平台运营配置统一进配置中心，详见 [`sys-config.md`](sys-config.md)（含 graph / memory / mcp / tools / checkpoint 盘点、Redis 刷新、Bean 装配调整）。

本方案相关约定摘要：

- **业务总开关** `graph.enabled` 在 sys_config（Redis 缓存，取值 **`1`/`0`**，见 [`sys-config.md`](sys-config.md)），不是 yml Conditional。
- **资源级** `knowledge_base.enable_graph` / `graph_model_code` 仍在知识库表（`enable_graph` 同为 0/1）。
- AGE：**引入即装配** `GraphStore`；门闩为  
  `isYes(graph.enabled) && ready && isYes(kb.enable_graph)`。
- 知识库召回专用 key：`knowledge.graph.*`、`knowledge.recall.*`（见 sys-config 运营级表）。

设置页分组含「图谱」「知识库召回」；与记忆/工具等共用同一套 Settings API。

## 模块改动

| 位置 | 改动 |
| --- | --- |
| `kernel` / `app` | 配置中心见 [`sys-config.md`](sys-config.md)；本方案消费 `graph.*` / `knowledge.*` |
| `knowledge` | Indexer、Processor、并发召回 Facade、库/文档字段、graph view/regraph |
| `memory-pg` | 过 `graph.enabled`；总开关改读 `memory.enabled` |
| `tool` | MCP / disclosure 改读 sys_config |
| `runtime` | checkpoint 改读 sys_config |
| `KnowledgePortAdapter` | 委托 Facade |
| `ChatServiceImpl` | 只调 Port/Facade |
| `graph-api` / age | expand/view 带边；去掉运营 enabled Conditional |
| `web` | 系统设置、库级开关、图谱页、regraph |
| `sql/01_sys_config.sql` | `sys_config` 种子 + 知识库/文档 GraphRAG 列补齐 |

`ObjectProvider<GraphStore>` + `info().isReady()` + `isYes(graph.enabled)` 三重门闩。

## 失败与降级

| 情况 | 行为 |
| --- | --- |
| DB `graph.enabled=0` | 全平台不写图、不扩边；库级开关无效 |
| `enable_graph=0`（单库） | 该库不写图、不扩边；仍走向量∥全文 |
| GraphStore 不可用 / not ready | 同总开关关闭；打日志 |
| LLM 抽取失败 | 向量保留；`graph_status=失败`；可 regraph |
| 单路检索超时/失败 | 该路空；另一路照常 RRF |
| 单种子 expand 失败 | 跳过该种子 |
| 单库超时 | 该库空上下文；其他库结果仍返回 |

## 交付清单（一次做完）

- [x] 配置中心（见 [`sys-config.md`](sys-config.md)）：含 graph/memory/mcp/tools/checkpoint/knowledge.*
- [x] LLM 抽实体/关系 + 写图 + 删文档/reembed/regraph
- [x] 向量∥全文并发 RRF；图扩边；专用线程池与超时；多库并行 Facade（`KnowledgeRetrieveFacade` + Semaphore）
- [x] Knowledge / Memory / 图谱页统一读 `graph.enabled` 门闩
- [x] `KnowledgePort` 统一走 Facade（含可选图扩边）；CHAT 与 rag-node 经 Port / Facade；多库 `retrieveAll`
- [x] 控制台：总开关与设置页、库级开关、状态、重试、知识图谱只读页（Vue Flow 画布 + 展开邻居）
- [x] graph view/expand 返回边（`expandNeighborhood` + `GET /api/kb/{baseCode}/graph`，支持 `startKey`）；`GET .../graph/summary`；删文档后清理无边 Entity；画布力导向布局；多库总超时 deadline

## 验收

1. 未开图：召回正确；向量与全文并行。  
2. 设置打开 `graph.enabled` + AGE ready + 库 `enable_graph`：上传/重嵌后有图；`graph_status=已写入`。  
3. 设置关闭 `graph.enabled`：各库 `enable_graph=1` 也不写图/不扩边；图谱页有明确提示。  
4. 只绑 A、B：结果与扩边仅来自 A、B。  
5. 多库并行且提示词块顺序稳定。  
6. AGE 未就绪时对话与向量化正常。  
7. 删文档无残留；regraph 可修复。  
8. 图谱页可浏览、按文档过滤、展开邻居。  
9. 单路失败/超时不导致整次聊天 500。  
10. 改配置后 Redis 中对应 key 已更新；再读即新值（多实例下各实例读到同一 Redis）。

## 与长期记忆

| | 知识库图 | 长期记忆图 |
| --- | --- | --- |
| scope | `kb:{baseCode}` | `mem_{user}_{agent}` |
| 内容 | 文档实体关系 | 用户记忆实体 |
| 触发 | 文档流水线 | remember / recall |
| 召回 | KnowledgePort | MemoryPort |

共用 `GraphStore`，数据与授权空间分离。
