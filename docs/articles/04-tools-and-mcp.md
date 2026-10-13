# 工具与 MCP：目录、连接、再绑定

Agent 不会直接「连上某个 MCP URL」。链路是：**连接 → 同步进工具目录 → Agent 绑定工具**。这样权限、审计、启停都落在 `ai_tool` 一层。

## 三张概念表

| 概念 | 存哪儿（概念） | 作用 |
| --- | --- | --- |
| MCP 连接 | `ai_mcp_connection` | 远端地址、鉴权、启用状态 |
| 工具 | `ai_tool` | 统一目录；`source_type` 区分本地 / MCP 同步等 |
| Agent 绑定 | agent–tool 关联 | 运行时只加载绑过的工具 |

控制台路径大致是：MCP 连接管理 → 同步 → 工具列表核对 → Agent 编辑页勾选工具。

接口前缀（见根 README）：`/api/tools`、`/api/mcp/connections`。

## 为什么不同 Agent 直连 MCP

1. **同一工具复用**：多个 agent 共用「查订单」，改一次描述或开关即可。  
2. **同步可重入**：远端工具列表变了，重新同步进目录，再决定是否给某 agent 放开。  
3. **观测统一**：轨迹里记的是工具名 / 入参出参，不依赖某次连接字符串。

## 本地工具与 MCP 工具

本地工具：代码里注册、进目录，适合强事务、必须走你们 DAO 的操作。

MCP 工具：适合浏览器、第三方 SaaS、已有 MCP Server。运行开关：`DEEPSTACK_MCP_ENABLED`（默认 `true`）。

工具披露模式：`DEEPSTACK_TOOLS_DISCLOSURE_MODE`（`off` / `progressive`），控制是否向模型渐进暴露工具列表，避免一次塞太多 schema。

## 绑定与安全

- 只绑需要的工具；「超级 Agent 绑全部」会放大误调用面。  
- 写库、打款类工具务必配 HITL（见 [06](06-hitl-human-in-the-loop.md)）。  
- 轨迹脱敏（F1）会处理 API Key、Bearer、手机号等；仍不要把密钥放进 Prompt。

## 排障顺序

1. 连接能否通、是否 enabled  
2. 同步后 `ai_tool` 是否出现对应条目  
3. Agent 是否绑定、工具是否启用  
4. 模型是否支持 function / tool calling，以及 `api_model_name` 是否配对  

下一篇：[知识库与图谱增强检索](05-knowledge-and-graphrag.md)。
