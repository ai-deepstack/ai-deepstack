# 智能体可观测性

面向 **CHAT / GRAPH 智能体** 的可观测能力梳理：现状、缺口、建议落地项。未做压测；条目按排查与运营价值排列。

相关实现：`TraceConfig` / `TraceModeEnum`、`WorkflowStreamEvent`、`AgentWorkflowRun`、`AgentChatOrchestrator`、`WorkflowEngineHealthIndicator`、Playground SSE `usage`。

---

## 1. 要解决什么问题

| 角色 | 典型问题 |
| --- | --- |
| 开发 / 排障 | 某次对话卡在哪一节点？超时是模型、RAG、工具还是 HITL？ |
| 运营 / 产品 | 某智能体成功率、耗时、token 成本趋势？用户卡在确认卡片多久？ |
| SRE | 图引擎是否过载？模型 / MCP / 知识库下游是否集体变慢？ |
| 评测 / 迭代 | 改图或改 prompt 后质量与成本是否变好？ |

可观测性通常拆四层，本仓库宜按此推进：

1. **日志（Logs）** — 单次请求可检索的上下文与错误栈  
2. **轨迹（Traces）** — 一次运行的节点 / 工具 / 模型时间线  
3. **指标（Metrics）** — 聚合的成功率、延迟、token、队列深度  
4. **产品面（Run UI）** — 运营可查的历史运行与节点详情  

---

## 2. 现状（已有）

### 2.1 轨迹模式（GRAPH）

`TraceConfig` 三级解析：请求覆盖 → `ai_agent.trace_mode` / `stream_progress` → 图 JSON `executionConfig`。

| 模式 | 行为 |
| --- | --- |
| `NONE` | 不写 `nodeExecutions`，不推节点进度 |
| `RECORD` | 节点结果写入 state / 响应的 `nodeExecutions`（input/output 摘要、durationMs、status） |
| `STREAM` | RECORD + SSE 推送节点进度（`streamProgress`） |

`WorkflowStreamEvent` 事件类型：

| eventType | 含义 |
| --- | --- |
| `NODE_START` / `NODE_COMPLETE` | 节点起止（含耗时、失败信息） |
| `LLM_STREAM` | 节点内流式 token |
| `CARD` / `CARD_STATUS` | 卡片产出与生成阶段 |
| `WORKFLOW_COMPLETE` | 整图结束（含 `nodeExecutions`） |

### 2.2 运行落库（CHAT + GRAPH）

表 `agent_workflow_run`（`AgentWorkflowRun`）经 `AgentRunService` 统一写入：

| 路径 | 写入点 |
| --- | --- |
| Chat GRAPH | `AgentChatOrchestrator` sync/stream |
| Chat CHAT | `ChatServiceImpl` sync/stream（含 `stages` 阶段耗时） |
| 图试跑 | `AgentGraphTestService` |
| HITL resume | `HitlPortAdapter` / `AgentGraphTestService.resume`（`hitl_suspended_at` / `hitl_wait_ms`） |

关键字段：`agent_code`、`orchestrate_mode`、`trace_id`（Micrometer MDC）、`thread_id`、`status`、`error_code`、`node_executions`、`stages`、`total_tokens`、`duration_ms`。

查询 API：`GET /api/agent-runs/overview`、`GET /api/agent-runs`、`GET /api/agent-runs/{id}`。前端「运行可观测」页：`/runs`。

### 2.3 指标与关联 ID

- Micrometer Tracing（Brave）写 MDC `traceId`/`spanId`；异步池 `MdcTaskDecorator` 传递。
- 业务指标：`AgentRunMetrics`（`agent.run.count` / `duration` / `error` / `tokens` / `hitl.wait` / `node.duration`）。
- 错误分类：`AgentRunErrorCode`（模型超时、MCP、知识库、取消、HITL 等）。

### 2.4 日志与健康

- 日志格式预留 `%X{traceId}` / `%X{spanId}`；API `Response` 可读 MDC `traceId` 作 `requestId`。
- `WorkflowEngineHealthIndicator`：编译缓存大小、在跑执行数（过高标 DOWN）；概览页展示同数字。
- Actuator 默认暴露 `health` / `info`。

### 2.5 前端

- Playground：流式内容 + `usage`（含 `runId`/`traceId`）；GRAPH 可跟节点/卡片事件。
- GraphEditor：试跑对话框，可查 run 状态 / `nodeExecutions`。
- **运行可观测**（`/runs`）：概览 KPI + 失败 Top + 慢请求 + 列表筛选 + 详情（stages / 节点时间线）。

---

## 3. 概览页（建议加）

**要加。** 运行列表/详情解决「查一次」；概览解决「今天整体怎么样、先看哪」。放在后台一级入口（如「可观测 / 运行概览」），与 Agents、Playground、GraphEditor 并列，不替代后两者。

### 3.1 谁看什么

| 角色 | 进概览先看 | 点进去去哪 |
| --- | --- | --- |
| 运营 / 产品 | 今日调用量、成功率、P95 耗时、token 用量、HITL 等待中数量、失败 Top 智能体 | 运行列表（按智能体/状态筛）→ 单次详情 |
| 开发 / 排障 | 近 1h 失败数与错误分类、慢请求、引擎在跑数、下游超时提示 | 失败 run 详情（节点时间线）→ 复制 `runId`/`traceId` 搜日志 |
| SRE | 引擎 health、线程池/队列是否打满、模型/MCP/知识库错误率尖峰 | Actuator / 监控大盘；必要时下钻 run |

### 3.2 页面区块（一屏内）

默认时间窗：**今天** / **近 24h**（可切 7 天）。筛选：智能体、编排模式（CHAT/GRAPH）、环境（若有）。

```text
┌─ 总览 KPI ─────────────────────────────────────────────┐
│ 调用次数 | 成功率 | P50/P95 耗时 | Token 合计 | HITL 等待中 │
└────────────────────────────────────────────────────────┘
┌─ 健康条 ───────────────────────────────────────────────┐
│ 图引擎 running/cache | 模型超时 | MCP 失败 | 知识库超时   │
│ （绿/黄/红；无 Metrics 前可用 health + 近窗 run 聚合）   │
└────────────────────────────────────────────────────────┘
┌─ 趋势（简）──┐  ┌─ 失败 Top 智能体 ──┐  ┌─ 慢请求 Top ──┐
│ 调用/失败折线 │  │ agent + 失败次数   │  │ run + 耗时     │
└──────────────┘  └───────────────────┘  └───────────────┘
┌─ 最近失败 / 等待人工 ──────────────────────────────────┐
│ 表格：时间 | 智能体 | 模式 | 状态 | 耗时 | runId → 详情  │
└────────────────────────────────────────────────────────┘
```

**不做进概览的**（避免一屏变控制台）：完整节点时间线、prompt 原文、会话聊天气泡 —— 留在运行详情 / Playground。

### 3.3 与列表、详情的分工

| 页面 | 职责 |
| --- | --- |
| **概览** | 聚合：健康、KPI、趋势、异常入口 |
| **运行列表** | 检索：筛选、分页、导出（可选） |
| **运行详情** | 单次：状态、token、节点时间线、错误、`traceId` |
| Playground / 试跑 | 实时调试，不是历史运营 |

导航建议：概览卡片 / 表格行 → 列表（带预设筛选）或直达详情。

### 3.4 数据依赖（与落地项对应）

| 区块 | 最低数据 | 对应缺口 |
| --- | --- | --- |
| KPI / 最近失败 | `agent_workflow_run` 聚合（GRAPH 已有） | O2 列表 API；概览用同 API `groupBy`/统计接口 |
| CHAT 进 KPI | CHAT 轮次落库 | O3 |
| 健康条细项 | health + 错误分类 | O8；有 O5 后换成实时 Metrics |
| HITL 等待中 | 挂起/resume 时间或 status=`WAITING_HUMAN` 计数 | O6 |
| 慢请求 / 趋势 | `durationMs` + 时间索引 | O2；量大后再上 O5 |

**阶段切分**：先做「概览 v1」——只基于现有 GRAPH run 表做 KPI + 最近失败 + 跳转详情（可与 O2 同迭代）；CHAT/HITL/Metrics 进概览跟 O3/O5/O6 走。

### 3.5 概览 v1 验收

1. 打开概览能看到近 24h GRAPH 调用量、成功率、P95（或平均）耗时。  
2. 「最近失败」可点进该次运行详情（节点时间线）。  
3. 引擎在跑数来自现有 health（或 run 表 RUNNING 计数），异常时有醒目状态。  
4. 无数据时空态明确（非报错白屏）。

---

## 4. 缺口与建议做的事项

### 4.1 建议先做（排障闭环）

| # | 项 | 说明 |
| --- | --- | --- |
| O1 | **请求级关联 ID 贯通** | `traceId`/`spanId` 用 Micrometer Tracing（`micrometer-tracing-bridge-brave`）写入 MDC，不自造 UUID；`Response.requestId` 读该值。异步池用 `MdcTaskDecorator` 拷贝 MDC。落库时把 `MDC traceId` 写入 `agent_workflow_run`。 |
| O2 | **运行列表 / 详情 + 概览 v1** | 列表与详情见上节；概览用同一数据源做 KPI、最近失败、健康条，作为运营/开发默认入口。 |
| O3 | **CHAT 轮次落库（精简）** | 非 GRAPH 也写一笔「对话运行」：agentCode、conversationId、latency、tokens、错误码、可选 RAG/记忆是否命中。可扩表现有表加 `orchestrate_mode`，或新建 `agent_chat_run`。 |
| O4 | **节点 / 阶段结构化字段** | `nodeExecutions` 约定稳定 schema（nodeId、type、durationMs、status、error、可选 `modelCode` / `toolCodes` / `kbCodes`）；LLM 节点补 prompt/completion tokens（若 SDK 可得）。便于 UI 与后续指标聚合。 |

### 4.2 建议随后做（运营与告警）

| # | 项 | 说明 |
| --- | --- | --- |
| O5 | **Micrometer 业务指标** | 例：`agent.run.count{agent,mode,status}`、`agent.run.duration`、`agent.node.duration{type}`、`agent.tokens{agent}`、`agent.hitl.wait`、`agent.tool.error`、池队列深度（workflow / chat-ctx / kb-*）。对接 Prometheus / 云监控。概览健康条可改为读 Metrics。 |
| O6 | **HITL 可观测** | 记录挂起时刻、resume 时刻、等待时长、卡片类型、确认/拒绝/超时；与 `threadId`、runId 关联。概览展示「等待中」计数。 |
| O7 | **下游依赖切片** | 单次运行内分阶段计时并打点：拼 prompt（画像/RAG/记忆）、模型调用、工具/MCP、图谱 expand。可落在 span 或 `nodeExecutions` extras。 |
| O8 | **错误分类** | 统一错误码：模型超时、MCP 失败、知识库超时、校验失败、用户取消、HITL 超时；写入 run 表与指标 label，避免只靠自由文本 `errorMessage`。 |

### 4.3 可选增强（平台级）

| # | 项 | 说明 |
| --- | --- | --- |
| O9 | **OpenTelemetry 分布式追踪** | 与现有 `traceId` 对齐；跨 JVM / 外部 MCP 时再上。短期 MDC + run 表足够。 |
| O10 | **采样与脱敏** | RECORD/STREAM 下 input/output 截断策略可配置；生产默认截断密钥、手机号、长原文；调试会话可提高采样。 |
| O11 | **评测挂钩** | 导出 run + 节点轨迹到离线评测集；与 prompt/图版本（已有 `definitionSnapshot`）对比。 |
| O12 | **告警规则** | 基于 O5：错误率、P99 延迟、HITL 积压、引擎 `runningExecutions`、模型 5xx/超时。 |

---

## 5. 推荐落地顺序

```text
阶段 1（排障）  O1 关联 ID → O4 轨迹 schema 固化 → O2 运行列表/详情 + 概览 v1
阶段 2（覆盖）  O3 CHAT 落库 → O6 HITL 等待 → O7 阶段计时（概览补全 CHAT/HITL）
阶段 3（运营）  O5 Metrics → O8 错误分类 → O12 告警（概览健康条升级）
阶段 4（可选）  O9 OTel → O10 采样脱敏 → O11 评测导出
```

原则：

- **GRAPH 以 `agent_workflow_run` + TraceMode 为事实源**，先补查询与 UI，少重复造「第二套轨迹」。
- **CHAT 先补「一轮一记」**，再考虑是否与 GRAPH 共用同一张 run 表。
- **指标从已有日志/落库字段衍生**，避免无 schema 就先上复杂 APM。
- **概览只做聚合与入口**，详细排障进详情，不把 Playground 嵌进概览。

---

## 6. 与现有能力的对应关系

| 能力 | 现状 | 对应缺口 |
| --- | --- | --- |
| 节点级轨迹 | RECORD / STREAM + `nodeExecutions` | O4 字段约定；O2 详情 UI |
| 运行持久化 | GRAPH / 试跑已写表 | O2 列表 + 概览；O3 CHAT |
| 实时进度 | SSE 事件 | 保持；O1 关联 ID |
| Token / 延迟 | CHAT usage；GRAPH totalTokens/durationMs | O5 聚合；GRAPH 分节点 token；概览 KPI |
| 健康检查 | 引擎 running / cache | 概览健康条；O5 池与下游；O12 |
| 分布式追踪 | 日志占位 | O1 先 MDC；O9 可选 |

---

## 7. 非目标（本期不做）

- 全量保存每次模型完整 prompt/response 原文（成本与隐私；用截断摘要 + 按需采样）。
- 替代业务会话记忆（ChatMemory）——可观测落库与对话历史职责分离。
- 在未统一 `traceId` / run schema 前引入多套 APM 产品。
- 概览页做成全能运维大盘（日志检索、链路火焰图等交给专用工具）。

---

## 8. 验收参考（O1–O8 已落地）

1. 任意 CHAT/GRAPH 对话：用 `traceId` 或 `runId` 能在日志与 `agent_workflow_run` 对上。  
2. `/runs` 概览展示近 24h KPI，并能从失败/慢请求进详情（节点时间线 / stages）。  
3. CHAT 轮次写入 `orchestrate_mode=0`，`stages` 含 `userPromptMs`/`ragMs`/`memoryMs`/`llmMs`。  
4. HITL resume 后 `hitl_wait_ms` 有值；概览 `waitingHuman` 与表状态一致。  
5. Actuator Metrics 可见 `agent.run.*`；`traceMode=NONE` 仍可对话但不推节点进度。
