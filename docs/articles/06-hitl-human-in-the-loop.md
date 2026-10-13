# HITL：图中途停下来等人

HITL（Human-in-the-loop）解决的是：图已经跑到「要写库 / 要对外发消息」了，必须等人点头或改参数，不能让模型自行落锤。

依赖：图编排（GRAPH）+ checkpoint。环境变量 `DEEPSTACK_CHECKPOINT_ENABLED`（默认 `true`）。卡片与人机交互在 `ai-deepstack-card`。

## 交互形态

1. 运行执行到需确认的节点，状态挂起，写入 checkpoint。  
2. 前端通过对话卡片展示待确认内容（同意 / 驳回 / 改字段，以实际卡片类型为准）。  
3. 人提交后，运行从断点继续；超时则按策略失败或取消（生产功能 **F4**）。

CHAT 模式偏连续对话，也可以有确认类卡片，但「图中途恢复」是 GRAPH + checkpoint 的主战场。

## 什么该做人审

适合：

- 资金、权限变更、对外正式通知  
- 删除、批量变更  
- 模型置信度低或工具返回异常时的人工兜底  

不适合：

- 纯检索问答的每一句  
- 已有强校验且可自动重试的幂等读接口  

原则：**写操作默认考虑 HITL；读操作默认自动。**

## 超时与配额

超时（F4）避免「挂起运行」占着并发配额不放。配额（F2）按全站 / Agent / 用户限制 QPS、并发、日 token；挂起中的运行是否占并发，以实现与配置为准，上线前在试跑环境确认。

## 和轨迹、告警的关系

挂起、恢复、超时应能在运行轨迹里看到节点与阶段，便于客服与研发对账。失败率、超时率可进告警（F5）。可观测说明见 [`../agent-observability.md`](../agent-observability.md)。

## 配置入口

系统设置与 `SysConfigKeys` 中与 HITL / checkpoint / 告警相关的键；缺省在 `SysConfigFallback`。改法见 [`../sys-config.md`](../sys-config.md)。

下一篇：[上线前加固](07-production-hardening.md)。
