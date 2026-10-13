# 上线前：脱敏、配额、版本、告警

对话能跑只是起点。单租户控制台形态下，F1–F10 已在仓库落地；清单与行为说明见 [`../production-features.md`](../production-features.md)。已有库补齐：`psql -f sql/03_production_features.sql`（前置 `01` / `02`）。

本文按「上线检查单」写，不重复贴全部配置键。

## 必做（与 Agent 无关）

- bootstrap / `03` SQL 已执行  
- `ai_model` 真实密钥；JWT、`admin` 密码已换  
- S3 桶与密钥不用仓库默认值  
- `management` 只暴露需要的端点  

## F1 轨迹脱敏

写入运行轨迹前截断并遮盖手机号、API Key、Bearer 等。配置键 `run.redact.*`，系统设置「轨迹脱敏」，代码默认开启。范围含用户消息、结果、节点输入输出、CHAT stages。

**上线**：保持开启；抽查一条含密钥的试跑，确认库里已打码。

## F2 限流与配额

`AgentQuotaService`：全站并发 → Agent QPS/并发/日 token → 登录用户同维度。超限 `429`，并记一条 `QUOTA_EXCEEDED` 拒绝运行。

**上线**：按容量设全站并发；给贵价模型的 agent 单独收紧日 token。

## F3 图版本发布与回滚

图定义可版本化；发布与回滚避免「改一半线上半新半旧」。

**上线**：生产只跑已发布版本；回滚演练一次。

## F4 HITL 超时

挂起太久要结束，释放配额并给业务明确失败态。见 [06](06-hitl-human-in-the-loop.md)。

## F5 告警

失败率、超时、配额拒绝等可通知（实现以当前告警通道为准），控制台有告警页可确认。

## F6–F10 速查

| 编号 | 用途 |
| --- | --- |
| F6 | 节点级指标，定位慢节点 |
| F7 | Agent 复制 / 子图片段，少手工拷贝 |
| F8 | 评测导出，做回归集 |
| F9 | 公共 / 私有模型，控制谁能用哪条密钥 |
| F10 | Agent 自身统计，运营看用量 |

## 可观测

O1–O8 与运行页说明见 [`../agent-observability.md`](../agent-observability.md)。排障顺序建议：配额拒绝 → 模型错误 → 工具失败 → 节点耗时 → 轨迹内容（已脱敏）。

## 配置改哪里

运营项进系统设置（`SysConfigPort`），不要改代码里的魔法数。键注释见 `SysConfigKeys`，文档见 [`../sys-config.md`](../sys-config.md)。

下一篇：[用 starter 嵌入](08-embed-with-starter.md)。
