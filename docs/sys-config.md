# 系统配置中心

运营级开关统一进 `deepstack.sys_config`，**Redis 缓存**，变更写库后刷新。知识库 / 图谱 / 工具 / 记忆 / Checkpoint 等模块共用，避免各写一套 yml。

GraphRAG 细节见 [`knowledge-graph-rag.md`](knowledge-graph-rag.md)。

## 分层原则

| 层 | 存哪 | 准则 |
| --- | --- | --- |
| 基础设施 | yml / 环境变量 | 无库/无 Redis 也要能启动：数据源、Redis 自身、S3、JWT 密钥 |
| 资源级 | 业务表 | 绑在某个资源上：某知识库 `enable_graph`、某 Agent 的 `enable_long_term_memory` |
| 运营级 | **sys_config + Redis** | 控制台可改、多模块联动、免发版 |

**业务总开关**（`graph.enabled`、`memory.enabled`、`mcp.enabled`、`checkpoint.enabled` 等）进配置中心，**不要**再用 `@ConditionalOnProperty` 卡死 Bean 创建。模块引入即装配；运行时读 sys_config + `ready` 探测决定是否真正干活。

### 是否类取值（强制）

与库表、接口一致，是否类一律用 **`0`/`1`**（文本）。

- `config_value` 存 `"1"` / `"0"`，**禁止**存 `true` / `false` / `yes` / `no`
- `value_type = yes_no`
- 消费侧用 `SysConfigPort.isYes(key)`（返回 `boolean`）
- 设置页用开关组件，提交时写 `1`/`0`
- 从旧 yml 迁入时：`true`→`1`，`false`→`0`（仅迁移/兜底解析，入库后只认 0/1）

非是否类照旧：字符串、普通整数、**`model`（存 model_code，控制台下拉选系统模型）**。

## 模块归属

| 工件 | 包 / 模块 | 职责 |
| --- | --- | --- |
| `SysConfigPort` + `SysConfigKeys` | `ai-deepstack-settings-api`（`org.deepstack.ai.settings`） | 只读 SPI + 键名常量；能力模块只依赖此 API |
| `SysConfigPortImpl` + Settings API | `ai-deepstack-app`（`org.deepstack.ai.app.settings`） | Redis→DB→yml→coded 读；写库 + Redis SET/DEL + invalidate PUBLISH；`GET/PUT /api/settings` |
| `YmlSysConfigPort` | app（`@ConditionalOnMissingBean`） | 无全量实现时的 yml/默认回落 |

## 现有模块盘点

### 留在 yml / 环境变量（基础设施）

| 现配置 | 说明 |
| --- | --- |
| `spring.datasource.*` / `DEEPSTACK_DB_*` | 数据库 |
| `spring.data.redis.*` / `DEEPSTACK_REDIS_*` | Redis（配置缓存本身依赖它） |
| `deepstack.s3.*` / `DEEPSTACK_S3_*` | 对象存储连接与密钥 |
| `deepstack.kb.bucket` / `DEEPSTACK_KB_BUCKET` | 知识库桶名（属存储拓扑，跟 S3 一起） |
| `deepstack.auth.jwt-secret` / `jwt-expire-hours` | 鉴权密钥与 TTL（安全敏感，部署注入） |
| `deepstack.graph.provider` / `age.default-graph` / `age.isolation` | 图谱实现选型与 AGE 图名/隔离策略（部署期结构） |
| `deepstack.memory.provider` | 记忆实现选型（如 `pg`） |
| `deepstack.checkpoint.provider` / `create-tables` | Checkpoint 实现与是否自动建表（部署期；`create-tables` 若进库也须 0/1） |
| `SERVER_PORT`、`DEEPSTACK_LOG_LEVEL`、management | 进程与可观测 |

### 迁入 sys_config（运营级）

| key | 现状来源 | 默认 | value_type | 说明 |
| --- | --- | --- | --- | --- |
| `graph.enabled` | `deepstack.graph.enabled` | `0` | yes_no | 平台图谱业务总开关 |
| `memory.enabled` | `deepstack.memory.enabled` | `0` | yes_no | 长期记忆总开关 |
| `memory.graph-by-default` | `deepstack.memory.graph-by-default` | `0` | yes_no | 未显式传 enableGraph 时是否默认走图 |
| `mcp.enabled` | `deepstack.mcp.enabled` | `1` | yes_no | MCP 总开关 |
| `mcp.tool-code-prefix` | `deepstack.mcp.tool-code-prefix` | `mcp_` | string | 同步进 `ai_tool` 的 code 前缀 |
| `tools.disclosure-mode` | `deepstack.tools.disclosure-mode` | `off` | string | `off` / `progressive` |
| `tools.progressive-full-below` | `deepstack.tools.progressive-full-below` | `5` | int | progressive 下小允许集仍全量披露 |
| `tools.search-top-k` | `deepstack.tools.search-top-k` | `8` | int | tool_search 返回条数 |
| `checkpoint.enabled` | `deepstack.checkpoint.enabled` | `1` | yes_no | 图 Checkpoint / HITL |
| `knowledge.graph.hops` | （新） | `2` | int | 知识库召回扩边跳数 |
| `knowledge.graph.max-chunks-per-extract` | （新） | `8` | int | 建图每批送模型 chunk 数 |
| `knowledge.graph.chat-model-code` | （新） | （空） | model | 全局默认抽取 CHAT 模型 |
| `knowledge.graph.graph-extra-top-k` | （新） | （空=跟 topK） | int | 图扩展追加上限 |
| `knowledge.recall.max-kb-concurrency` | （新） | `4` | int | 多库召回并行上限 |
| `knowledge.recall.max-expand-concurrency` | （新） | `8` | int | 单库多种子 expand 上限 |
| `knowledge.recall.timeout-ms` | （新） | `3000` | int | 单库召回超时 |

设置页分组：**图谱**、**长期记忆**、**工具 / MCP**、**Checkpoint**、**知识库召回**。

### 继续留在业务表（资源级）

| 字段 | 表 | 说明 |
| --- | --- | --- |
| `enable_graph`、`graph_model_code` | `knowledge_base` | 单库是否建图/扩边（`enable_graph` 已是 0/1）、抽取模型 |
| `graph_status`、`graph_error` | `knowledge_document` | 单文档写图状态 |
| `enable_long_term_memory`、`enable_graph_memory` | `ai_agent` | 单智能体记忆策略（已是 0/1） |
| MCP 连接、工具启停、模型启停等 | 各业务表 | 已有管理页；`enabled` 均为 0/1 |

## 形态

### 表

`deepstack.sys_config`：

- `id`（雪花 BIGINT 主键）
- `config_key`（唯一业务键）
- `config_name`（配置名，展示用）
- `config_value`（文本；是否类为 `"0"`/`"1"`）
- `value_type`（`string` / `yes_no` / `int` / `json` / `model`）
- `description`
- `updated_at`（触发器刷新）

独立脚本 [`sql/01_sys_config.sql`](../sql/01_sys_config.sql) 建表并登记运营 key（名称/类型；**种子 `config_value` 留空**，运行时回落 yml）；新库在 `00` 之后执行，已有库可只跑 `01`。若旧种子已写入非空值且希望改回 yml 驱动，可将对应行 `config_value` 置空并删 Redis `deepstack:sys_config:*`。

### Redis 与 yml 兼容

读优先级（高 → 低）：

1. **Redis**（仅缓存库内非空值，或设置页刚写入的值）
2. **DB** `sys_config.config_value`（非空才算显式配置；空串继续回落）
3. **yml / 环境变量**（`deepstack.*`，是否类兼容 `true`/`false` → `1`/`0`）
4. **代码默认**

yml 兜底**不写 Redis**，改环境变量后无需清缓存即可生效。设置页保存后以库为准，覆盖同 key 的 yml。

| 项 | 约定 |
| --- | --- |
| Key | `deepstack:sys_config:{config_key}` |
| Value | 与表 `config_value` 相同字符串（是否类即 `"0"`/`"1"`） |
| 写非空 | 更新 DB 后 `SET` 对应 key |
| 写空串 | 更新 DB 为空后 **`DEL` Redis**（不 `SET` 空串），以便回落 yml |
| 多实例 | `PUBLISH deepstack:sys_config:invalidate`（payload=key 或 `*`）；订阅方 DEL 本地缓存 |
| 客户端 | 与登录会话同一 `StringRedisTemplate` |

```
PUT /api/settings
  → UPDATE sys_config
  → SET 或 DEL deepstack:sys_config:{key}
  → PUBLISH deepstack:sys_config:invalidate
```

### 运行时 API

- `SysConfigPort.getString(key)`
- `SysConfigPort.getInt(key, defaultValue)`
- `SysConfigPort.isYes(key)` — **是否类只用此方法**
- `GET /api/settings` → `List<SysConfigResponse>`，每项含：

| 字段 | 说明 |
| --- | --- |
| `id` | 雪花主键（仅库中已有行时有值） |
| `configKey` | 配置键 |
| `configName` | **配置名**（中文展示） |
| `configValue` | 当前有效值（是否类 `0`/`1`） |
| `valueType` | `string` / `yes_no` / `int` / `json` / **`model`** |
| `description` | 说明 |
| `valueName` | 是否类时「是/否」 |
| `modelType` | `valueType=model` 时：`0`=CHAT / `1`=EMBEDDING |
| `group` | 设置页分组 |
| `source` | 有效值来源：`db` \| `yml` \| `default` |

- `PUT /api/settings` body：`{ "values": { "<configKey>": "<configValue>" } }`

原 `@ConfigurationProperties` / `application.yml` 作部署期兜底；是否类 yml 可用 `true`/`false`，读入后规范为 `0`/`1`。MCP 前缀等运营项以 `SysConfigPort` 为准，不再在业务代码里 dual-read Properties。

### Bean 装配调整

| 模块 | 现状 | 目标 |
| --- | --- | --- |
| `AgeGraphConfiguration` | yml `enabled=true` 才建 Bean | 引入即建；业务 `isYes("graph.enabled")` + ready |
| `MemoryPgConfiguration` | yml `enabled=true` 才建 | 引入即建；业务 `isYes("memory.enabled")` |
| `CheckpointConfiguration` | Conditional | 运行时 `isYes("checkpoint.enabled")` |
| MCP | Properties `enabled` | `isYes("mcp.enabled")` |
| tools disclosure | Properties | 字符串 mode 仍用 getString；与是否无关 |

## 控制台

「系统设置」：列表以 **配置名** 为主、配置键为辅；是否项用开关；`valueType=model` 用模型下拉（`/api/models/options`）。JWT/S3 等不出现在此页。

## 交付

- [x] `sys_config` DDL + 种子（是否类默认 `0`/`1`）
- [x] `settings-api`（`SysConfigPort` + Keys）+ App 实现 + Redis 刷新/失效 + Settings API
- [x] 设置页（开关提交 0/1；`source` 字段）
- [x] 各模块读 `SysConfigPort`；弱化运营项 ConditionalOnProperty
- [ ] GraphRAG 门闩与 key 消费（见 knowledge-graph-rag；key 已入库）

## 验收

1. 改设置后 Redis 更新，再读即新值；空值保存后 Redis DEL，回落 yml。  
2. 是否类库内与 Redis 只有 `0`/`1`，无 `true`/`false`。  
3. `isYes("graph.enabled")` 等关断后能力停用且不 500。  
4. 未配 DB 行时回落 yml（布尔映射为 0/1）。  
5. JWT/S3/数据源仍仅来自环境。
