# 生产功能缺口

单租户控制台形态下，对话、图编排、工具（含 MCP）、知识库、HITL、长期记忆、运行可观测（O1–O8）已经可用。本文记录 **F1–F10 产品功能**（不含多租户）。

不做：多租户、按租户隔离的 API Key、拆微服务、发布 Maven Central。

相关：[`ROADMAP.md`](../ROADMAP.md)、[`agent-observability.md`](agent-observability.md)。

已有库补齐：`psql -f sql/03_production_features.sql`（前置 `01` / `02`）；删 `template` 列与历史 `tpl_*`：`psql -f sql/04_agent_copy_no_template.sql`。新库 compose 按 `00→03`（无模板列）。

---

## 1. 状态一览

| 编号 | 功能 | 状态 |
| --- | --- | --- |
| F1 | 轨迹脱敏 | 已实现 |
| F2 | 调用限流与配额 | 已实现 |
| F3 | 图版本发布与回滚 | 已实现 |
| F4 | HITL 超时 | 已实现 |
| F5 | 告警 | 已实现 |
| F6 | 节点级指标 | 已实现 |
| F7 | 智能体复制 / 子图片段 | 已实现 |
| F8 | 评测导出 | 已实现 |
| F9 | 公共 / 私有模型 | 已实现 |
| F10 | 智能体自身统计 | 已实现 |

---

## 2. 功能说明

### F1 轨迹脱敏

写入 `agent_workflow_run` 前由 `TrajectoryRedactor` 截断并遮盖手机号、API Key、Bearer。配置键：`run.redact.*`（系统设置「轨迹脱敏」；代码默认开启）。

作用范围：`user_message` / `result` / `node_executions` 输入输出 / CHAT `stages`。

### F2 调用限流与配额

`AgentQuotaService`：全站并发 → 智能体 QPS/并发/日 token → 登录账号 QPS/并发/日 token。超限 `429`，并 `reject` 一条 `QUOTA_EXCEEDED` 运行。

- 智能体额度：配置抽屉里的 QPS / 并发 / 每日 token。
- 账号与全站：`quota.*` 系统设置。

账号 = JWT `AiUser.id`，不是对话请求里的记忆 `userId`。

### F3 图版本发布与回滚

草稿 `graph_definition`；线上 `/api/chat` GRAPH 只跑 `published_graph_definition`。画布「保存草稿 / 发布 / 回滚」；试跑走草稿。

### F4 HITL 超时

`HitlTimeoutTask` 每 2 分钟扫描；超时后失败并错误码 `HITL_TIMEOUT`，尝试释放 checkpoint。超时优先用智能体 `hitlTimeoutMinutes`，否则 `hitl.timeout-minutes`。

### F5 告警

`AgentAlertTask` 每 5 分钟扫描错误率 / P95 / HITL 积压 / 在跑数；落库 `agent_alert_event`，可选 Webhook。控制台「告警」页可确认。

### F6 节点级指标

`WorkflowCompiler` 在节点完成处上报 `agent.node.duration`（标签 agent / type / status）。

### F7 智能体复制 / 子图片段

列表「复制」→ 新建编辑页预填源配置（编码不展示，后端生成）→ 保存时 `POST /api/agents` 带 `sourceAgentId`；名称全局唯一；新副本默认停用、清空发布态，并拷贝工具/知识库绑定。图编排里 `insert-fragment` 插入子图并重写节点 id（与复制无关）。

### F8 评测导出

运行可观测页「导出 JSONL」→ `GET /api/agent-runs/export`（已脱敏字段）。

### F9 公共 / 私有模型

`ai_model.visibility` + `ownerId`。下拉 = 公共 + 自己的私有。管理员可改公共模型。

### F10 智能体自身统计

卡片「统计」→ `/agents/:id/stats`；数字链到该智能体运行列表。

---

## 3. 明确不做

| 项 | 原因 |
| --- | --- |
| 多租户、租户级 API Key | 本期不做 |
| OpenTelemetry | 单进程用现有 `traceId` + 运行表足够 |
| 全量 prompt/response 原文入库 | 成本与隐私；用 F1 的截断摘要 |
| 拆微服务、Maven Central | 不是产品功能 |
