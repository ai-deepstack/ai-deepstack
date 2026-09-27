# 其他模块优化点

知识库 GraphRAG 的落地与剩余项见 [`knowledge-graph-rag-summary.md`](knowledge-graph-rag-summary.md)。本文只记 **knowledge 以外**（及跨模块）审代码时看到的点。未做压测，条目是结构问题，不是线上指标。

## 已落地

### 原建议 1–8

| # | 项 | 落点 |
| --- | --- | --- |
| 1 | 模型 HTTP connect/read 超时 | `AiChatClientFactory` / `EmbeddingClientFactory` |
| 2 | 长期记忆图扩边并发 + 批量回表 | `PgHybridLongTermMemoryStore` + `memoryRecallExecutor` |
| 3 | MCP 启动并行建连 | `McpConnectionBootstrap` |
| 4 | 模型配置变更 evict 客户端缓存 | `AiModelChangedEvent` → `AiModelClientCacheEvictListener` |
| 5 | MCP listTools 按连接缓存 | `McpToolBridge.listToolsCache` |
| 6 | 工具目录批量 IN | `AiToolService.listEnabledByCodes` + `DbToolCatalog.listByCodes` |
| 7 | 工作流跟踪不在池线程 join | `WorkflowCompiler.wrapWithTracking` |
| 8 | CHAT 画像 / RAG / 记忆并行拼 prompt | `ChatServiceImpl.buildSystemPrompt` |

### 复查后补齐（A/B + 随后项）

| 项 | 落点 |
| --- | --- |
| A. 拼 prompt 与多库召回同池嵌套 | 独立 `chatContextExecutor`；RAG 仍走 `knowledgeMultiKbExecutor` |
| B. 卡片 ChatClient 握旧模型 | `ChatCardServiceImpl` 不再缓存 ChatClient，每次 `Factory.getChatModel` 再 builder |
| 拼 prompt 总时限 | `chat.context.timeout-ms`（默认 10s）；`allOf.get` + 超时取消 |
| 工具回调冷启动批量 | `CatalogToolCallbackResolver.resolveByCodes` 未命中走 `listByCodes` |
| 智能体详情绑库批量 | `KnowledgeBaseService.listByBaseCodes` + `AgentController.fillKnowledgeBases` |
| 文档 chunk 批量写库 | `DocumentProcessorImpl.embedAndSave` → `knowledgeChunkService.saveBatch` |

## 先不动

| 项 | 原因 |
| --- | --- |
| sys_config Redis 无 TTL | 写路径 SET/DEL + 失效广播，空值回源 DB |
| Checkpoint 引入即装配 | 开关在 `checkpoint.enabled` |
| 工具渐进披露默认 off | 配置行为 |
| MCP 单连接 `requestTimeout` | `McpClientRegistry` 已按连接配置 |
| `ToolDescriptionResolver` 缓存 | `@Tool` 注解运行期不变 |
| `WorkflowCompiler` 编译缓存 | 存图时已 `invalidateCache(agentId)` |
| `LLMReasonNode.blockLast` | 流式排空设计 |
| `AgeGraphStore.ensuredGraphs` | PROPERTY 模式图数量有限 |

## 和知识库的关系

知识库侧已处理：多库独立线程池、阶段 A 语句超时、embedding 独立池、删库清 scope、孤儿实体、图谱 summary / 画布、阶段 B 并发 expand。CHAT 侧用 `chatContextExecutor`，勿再复用 `knowledgeMultiKbExecutor` 做 prompt 三段并行。
