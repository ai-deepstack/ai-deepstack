# Agent 是配置实体，不是一段 Prompt

很多项目把「Agent」等同于一段 system prompt + 几个 tool 名字。DeepStack 里，Agent 是落库的配置实体：模型、编排模式、工具绑定、知识库、意图、图定义都挂在同一个 `agentCode` 上。

## 一张表里的核心字段（概念）

表大致是 `ai_agent`（以仓库 SQL / 实体为准）。运营时关心这些：

| 概念 | 含义 |
| --- | --- |
| `agentCode` | 对外稳定标识，对话接口只认它 |
| 编排模式 | CHAT（对话链）或 GRAPH（LangGraph4j 图） |
| 模型 | 指向 `ai_model.model_code`，运行时按码建客户端 |
| 工具绑定 | 绑的是 `ai_tool` 里的工具，不是 MCP 连接本身 |
| 知识库 | 可选；检索结果进上下文 |
| 图定义 | `orchestrate_mode = GRAPH` 时存在 `graph_definition`（及版本，见生产功能） |

控制台「Agent」页改的就是这些；试跑与正式对话走同一套解析逻辑，避免「演示一套、生产一套」。

## CHAT 与 GRAPH

**CHAT**：适合单轮 / 多轮问答、简单工具调用。短记忆、结构化输出在 chat 模块里处理。

**GRAPH**：节点、边、条件分支落在图定义里。适合多步业务（查库 → 汇总 → 等人确认 → 写回）。运行时由 `ai-deepstack-runtime` 编译执行；需要停下来等人时走 HITL（见 [06](06-hitl-human-in-the-loop.md)）。

图定义挂在 Agent 上，版本发布 / 回滚是生产功能 F3。

## 意图与卡片

意图（intent）用来做路由或业务标签，避免所有请求塞进同一个超级 prompt。

对话卡片（card）配合 HITL：图中途需要人点同意 / 改参数时，前端渲染卡片，后端写 checkpoint，超时策略见 F4。

## 为什么做成配置实体

1. **权限与配额按 Agent 打** — 全站并发之外，可按 agent / 登录用户限 QPS、日 token（F2）。  
2. **轨迹可归因** — 运行记录带 agent、节点、阶段，可观测与告警才有挂点（见 [agent-observability.md](../agent-observability.md)）。  
3. **Agent 复制** — F7 把「相似的 Agent」变成复制配置进入新建编辑页，编码后端生成，名称全局唯一。

## 实践建议

- `agentCode` 用稳定业务名（如 `order_refund`），不要用临时 `test1`。  
- Prompt 里写「角色与边界」；流程分支尽量进图节点，而不是超长 if-else 文案。  
- 工具先注册进目录再绑定；MCP 先同步成工具（下一篇 [04](04-tools-and-mcp.md)）。

下一篇：[Port / SPI 架构](03-port-spi-architecture.md)。
