# ROADMAP

## 目标产品模型（终态）

**唯一一等公民：Agent。取消「场景」概念；取消「独立工作流模块再绑定」的产品形态。**

| 概念 | 终态定义 |
|------|----------|
| **Agent** | 可部署、可调用、可版本化的智能体。对外标识：`agentCode`（`/api/chat`、`/api/agents/**`） |
| **编排** | Agent 属性：`CHAT`（直连模型+工具+RAG）或 `GRAPH`（`graphDefinition` 内嵌 LangGraph） |
| **画布 / 试跑** | 挂在 Agent 下：`GET/PUT /api/agents/{id}/graph`、`POST .../graph/test` |
| **节点类型 / 工具 / 模型 / 知识库** | 平台级目录，供多个 Agent 引用 |
| **复用** | 高级能力用「从模板复制 / 子图库」；不做共享 Workflow 实体外键绑定 |

原则：配置与编排在同一 Agent 资源上版本化；调用方只认识 Agent。

## Phase 1（当前）

- [x] 多模块：`ai-deepstack-*`（kernel / infra / engine / 能力域 / runtime / starter / app / web）
- [x] 包名 → `org.deepstack.ai`
- [x] 统一 `/api/**`，不区分 B/C
- [x] 参考应用 JWT 登录 + 控制台
- [x] 去掉阿里云图谱与长期记忆
- [x] `ai-deepstack-starter` 聚合依赖

## Phase 2

- [x] **Agent 模型收敛**：Scene → `ai_agent`；`orchestrateMode` + 内嵌 `graphDefinition`；去掉独立 `agent_workflow` 绑定
- [x] **Agent 内可视化画布**（控制台 GraphEditor；API 已就绪）
- [ ] 更细的 AutoConfiguration（可选 Web）
- [x] 本地长期记忆：`ai-deepstack-memory`（`memory-api` + `memory-pg`，PG 主存 + 可选 AGE）
- [x] 本地图谱：`ai-deepstack-graph`（`graph-api` + `graph-age`，默认关闭）
- [x] Checkpoint / HITL 恢复（PostgresSaver + card-gate-node / 出卡自动挂起 + 通用确认卡 resume）
- [ ] 发布 Maven Central（BOM）
- [x] 收紧 engine 对业务的端口抽象（减少 runtime 胶水面；Adapter 下沉；`GraphRuntime` / `RunContext`；工具 Catalog+Resolver）
- [x] Tool Search / 渐进披露（Index hydrate + `disclosure-mode`；默认 off；GRAPH 每轮刷新 exposed）
- [x] MCP 工具桥（`ai_mcp_connection` + `ai_tool` MCP 行；`McpClientRegistry` / Bridge / Sync；控制台 `/mcp`）

## Phase 3

- [ ] Agent 模板 / 子图复用（复制而非共享绑定）
- [ ] 多租户 / API Key 对外通道
- [ ] 微服务拆分（按需）
