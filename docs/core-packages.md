# Deepstack 分包（当前）

根包：`org.deepstack.ai`

```text
org.deepstack.ai
├── kernel.* / infra.* / aimodel.* / tool.* / knowledge.* / card.*
├── settings.*                # settings-api：SysConfigPort + SysConfigKeys
├── graph.*                   # api（SPI）+ age（可选实现）
├── memory.*                  # api + pg
├── agent.* / chat.*          # 编排（注入 toolCodes / 绑定允许集）
├── runtime.*                 # GraphRuntime / RunContext / spi.Port / Compiler / checkpoint
│   ├── spi.*                 # Model/Tool/Knowledge/Card/Hitl Port
│   ├── spi.tool.*            # ToolDescriptorIndex / ToolDisclosureSession
│   └── engine.*              # GraphSpec / WorkflowState / 全部节点
└── app.*                     # 参考应用（LoginInterceptor、sys_config 实现等）
    ├── settings.*            # SysConfigPortImpl + SettingsController
    └── spi.*                 # 仅 App 专属（如 AppUserProfileLoader）
```

各业务模块统一分层：`web` · `service`(+`impl`) · `mapper` · `model.entity` · `model.dto` ·（可选）`spi` / `config`。

## Port 与 Adapter 归属

| Port | Adapter 模块 |
|------|----------------|
| `SysConfigPort` | `app`（`SysConfigPortImpl`；SPI 在 `settings-api`） |
| `ModelPort` | `infra` |
| `ToolPort` | `tool`（`ToolCatalog` + `CatalogToolCallbackResolver`） |
| `KnowledgePort` | `knowledge` |
| `CardPort` | `card` |
| `HitlPort` | `agent`（经 `GraphRuntime` resume） |
| `MemoryPort` | `memory-pg` |

- 业务键：`modelCode` / `toolCode` / `baseCode`；`CardPort.afterEmitted(EmittedCard)`
- **能力模块实现 Port**；`runtime` 提供 `@ConditionalOnMissingBean` 空实现
- 编排 / HITL / 试跑依赖 **`GraphRuntime`**，不直接依赖 `WorkflowCompiler` 细节
- 请求态在 **`RunContext`**；`AgentNodeContext` 仅聚合 Port

## 工具分层

```text
ToolPort（机械 resolve）
    ▲
ToolPortAdapter → ToolCatalog(Db) + CatalogToolCallbackResolver(LOCAL Bean | MCP Bridge)
```

- **禁止**再使用已退役的 `ToolRegistry` 概念
- Agent ↔ Tool = **`ai_agent_tool` 绑定允许集**；Catalog 全量 ≠ 某 Agent 可用集
- **MCP**（包 `tool.mcp`，无独立模块）：连接表 `ai_mcp_connection`；同步进 `ai_tool`（`source_type=MCP`）；Agent 绑工具不绑连接；密钥不回传列表 API；总开关 / 前缀走 sys_config（`mcp.enabled`、`mcp.tool-code-prefix`）

## 工具披露 SPI

包：`org.deepstack.ai.runtime.spi.tool`

- `ToolDescriptorIndex`：真 Registry；启动时由 `ToolIndexHydrator` 从 `ToolCatalog` hydrate（仅摘要）
- `ToolDisclosureSession` + `ModeAwareToolDisclosureSessionFactory`：
  - `deepstack.tools.disclosure-mode=off`（默认）：绑定工具全量 resolve
  - `progressive`：元工具 `tool_search` / `load_tools`；允许集 ≤ `progressive-full-below` 时仍全量
- GRAPH：`LLMReasonNode` 每轮刷新 `exposedToolCallbacks`
- CHAT：经同一 Factory 取 exposed（ChatClient 单次挂载；同轮 load 后需再请求或依赖客户端 tool loop）

配置见 sys_config（`tools.*`）与 yml 兜底 `deepstack.tools.*`。

知识图谱：`knowledge` 只依赖 `graph-api`；AGE 由 starter/app 引入。  
长期记忆：`chat`/`runtime` 依赖 `memory-api`；运行时总开关 `memory.enabled`（sys_config，默认关）。

鉴权：登录信息 Redis；`LoginInterceptor`；`@Anonymous`；`@LoginUser`。

第三方：依赖 runtime + 能力模块（自带 Adapter），或自行实现 Port。

其他模块的优化备忘（非知识库）：[`module-optimization-notes.md`](module-optimization-notes.md)。
