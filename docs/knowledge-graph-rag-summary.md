# GraphRAG 实现总结

本文是 [`knowledge-graph-rag.md`](knowledge-graph-rag.md) 方案的落地说明：已交付能力、关键路径、配置与排查要点。

## 1. 交付概览

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| LLM 抽实体/关系写 AGE | 已交付 | embedding 成功后异步建图；失败不回滚向量 |
| 向量 ∥ 全文 RRF | 已交付 | 阶段 A 并发；语句超时 + embedding 独立池 |
| 图谱扩边召回 | 已交付 | 阶段 B 对 seeds 并发 expand；`graphScore = 0.7/(1+hop)` |
| 多库并行 Facade | 已交付 | Semaphore 限流 + 总超时 deadline；CHAT / rag-node 统一入口 |
| 控制台图谱页 | 已交付 | Vue Flow 只读、力导向、展开邻居、summary |
| 删文档/删库清图 | 已交付 | 删文档清 Doc/Chunk + 孤儿 Entity；删库 `deleteScope` |

非目标（未做）：用图替代向量主召回；跨库 hop；前端手工改图；与长期记忆共用 scope。

## 2. 架构与数据流

```
入库：解析 → 切片 → embedding → [门闩] → LLM 抽取 → AGE upsert
召回（单库）：
  阶段 A：全文 SQL ∥ (embed → 向量 SQL) → RRF → seeds
  阶段 B：seeds 并发 expand → 补 chunk → 按分截断
召回（多库）：
  KnowledgeRetrieveFacade.retrieveAll → 每库 retrieveOne（限流）→ 按绑定顺序拼接
CHAT / rag-node → KnowledgePort / Facade（禁止业务侧直接串行 hybridSearch）
```

### 门闩

写图与扩边同时满足：

1. `sys_config.graph.enabled = 1`
2. `GraphStore.info().ready`
3. `knowledge_base.enable_graph = 1`

关闭任一门闩时：对话仍走向量∥全文，行为与未开图一致。

### 图隔离

| 用途 | scope |
| --- | --- |
| 知识库 | `kb:{baseCode}` |
| 长期记忆 | `mem_{user}_{agent}`（不变） |

节点：`Document(doc_*)` / `Chunk(chk_*)` / `Entity(ent_*)`  
边：`CONTAINS` / `MENTIONS` / `RELATED_TO` / `ABOUT`

## 3. 关键代码

| 模块 | 类 | 职责 |
| --- | --- | --- |
| knowledge | `KnowledgeGraphIndexer` | 建图、regraph、删文档清图、孤儿清理 |
| knowledge | `KnowledgeGraphExtractor` | CHAT 模型 JSON 抽实体/关系 |
| knowledge | `KnowledgeChunkServiceImpl` | hybrid 阶段 A/B、超时、hop 计分 |
| knowledge | `KnowledgeRetrieveFacade` | 单库 / 多库并行编排 |
| knowledge | `KnowledgePortAdapter` | runtime `KnowledgePort` 适配 |
| knowledge | `KnowledgeGraphController` | `/graph`、`/graph/summary` |
| knowledge | `RecallStatementTimeoutInterceptor` | 召回 SQL `queryTimeout` |
| graph-age | `AgeGraphStore` | expand（含 hop）、summarize、deleteOrphanEntities |
| chat | `ChatServiceImpl` | RAG 只调 Facade |
| runtime | `RAGRetrieveNode` | `retrieveAll` 多库并行 |
| runtime | `AsyncConfig` | `knowledgeRecallExecutor` / `knowledgeMultiKbExecutor` / `knowledgeEmbedExecutor` |
| web | `KnowledgeGraph.vue` | Vue Flow 画布 + 力导向 + summary |

## 4. 线程池与超时

| Bean | 用途 | 规模（默认） |
| --- | --- | --- |
| `knowledgeProcessExecutor` | 文档解析 / 写图流水线 | core 2 / max 8 |
| `knowledgeRecallExecutor` | 库内全文、向量 SQL、expand | core 4 / max 16 |
| `knowledgeMultiKbExecutor` | 多库编排（禁止与 recall 同池） | core 2 / max 8 |
| `knowledgeEmbedExecutor` | embedding HTTP | core 2 / max 4 |

| 配置 key | 含义 | 默认 |
| --- | --- | --- |
| `knowledge.recall.timeout-ms` | 单库阶段 A/B、单库等待；多库总超时 = 该值 × ceil(库数/并发) | 3000 |
| `knowledge.recall.max-kb-concurrency` | 多库并行上限（硬顶 8） | 4 |
| `knowledge.recall.max-expand-concurrency` | 单库多种子 expand 上限 | 8 |
| `knowledge.graph.hops` | expand 跳数 | 2 |
| `knowledge.graph.extra-top-k` | 图扩展额外条数（空则 = topK） | （空→topK） |

阶段 A：SQL 经 ThreadLocal 设语句超时；外层 `get(awaitMs)`，超时按该路空结果继续 RRF。  
阶段 B：`allOf` 超时后 cancel 未完成 expand。  
多库：共用 deadline，超时库记空上下文，不拖垮整次对话。

## 5. API 与控制台

| 接口 | 说明 |
| --- | --- |
| `GET /api/kb/{baseCode}/graph` | 邻域节点+边；`documentId?` / `startKey?` / `hops` / `limit` |
| `GET /api/kb/{baseCode}/graph/summary` | 开关、就绪、节点/边约数、最近写图时间 |
| `PUT /api/kb/docs/{id}/regraph` | 仅重跑写图 |

控制台：

- 知识库：`enable_graph`、抽取模型、文档 `graph_status`、regraph
- 图谱页：`/knowledge/:baseCode/graph` — Vue Flow、图例、属性面板、展开邻居、力导向布局

## 6. 启用步骤

1. 系统设置打开 `graph.enabled`，确认 AGE / GraphStore ready（可看 summary 或 `/api/graph/info`）。
2. 知识库打开「启用图谱」，指定 CHAT 抽取模型（库级 → yml → 平台默认）。
3. 上传或重嵌文档，观察 `graph_status=已写入`。
4. 对话或 rag-node 绑定该库；开图后召回自动含扩边块。
5. 图谱页按文档或展开邻居浏览。

## 7. 失败与降级

| 情况 | 行为 |
| --- | --- |
| `graph.enabled=0` | 不写图、不扩边；summary/view 有明确文案 |
| 库未开 `enable_graph` | 该库不写图、不扩边；仍可只读已有图 |
| GraphStore 未就绪 | 同总开关关闭 |
| LLM 抽取失败 | 向量保留；`graph_status=失败`；可 regraph |
| 全文/向量单路超时或失败 | 该路空；另一路仍参与 RRF |
| 单种子 expand 失败 | 跳过该种子 |
| 单库 / 多库超时 | 该库空上下文；其它库照常 |
| 删文档 | `deleteByDocument` + 清理无 MENTIONS/ABOUT 的 Entity |
| 删库 | DB 删除后尽力 `deleteScope` |

## 8. 排查日志关键词

- 门闩：`graphWritable=false`
- 建图：`indexDocument` / `写图前 deleteByDocument` / `已 upsert Entity`
- 召回 A：`hybridSearch 阶段A` / `全文完成` / `向量完成` / `阶段A … 等待超时`
- 召回 B：`hybridSearch 阶段B` / `expand seed` / `阶段B 超时`
- 多库：`retrieveAll 启动` / `总超时` / `ok=, fail=`
- 清图：`deleteDocumentGraph` / `deleteOrphanEntities` / `clearGraphScope`
- 前端：浏览器控制台 `[kb-graph]`

## 9. 与方案文档关系

- 设计全文与验收清单：[`knowledge-graph-rag.md`](knowledge-graph-rag.md)
- 配置中心（含 graph / knowledge.recall.*）：[`sys-config.md`](sys-config.md)

方案中的终态清单已对齐实现；后续若再加能力，优先改方案文档，再同步本文第 1、3 节。
