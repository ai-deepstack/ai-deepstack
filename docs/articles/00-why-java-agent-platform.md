# 为什么还要再做一个 Java Agent 平台

多数团队聊 Agent，默认栈是 Python：LangChain、LangGraph、各种 notebook 里拼出来的链路。Java 这边往往是「先调一个 HTTP 封装」，业务状态、权限、事务还在 Spring 里，两边各管一半。

DeepStack 想解决的不是「再造一个 ChatGPT」，而是：**Agent 能力要落在现有 Java 工程里**——同一套登录、同一套库表、同一套发布流程。

## 常见痛点

1. **编排在 Python，业务在 Java**  
   调工具要跨语言 RPC；改一次意图要两边发版；排障时日志对不上。

2. **Prompt 散落在配置和代码里**  
   没有「Agent」实体：模型、工具、知识库、图定义各写各的，线上出问题不知道改哪条配置。

3. **演示能跑、上线缺件**  
   限流、脱敏、人机确认超时、图版本回滚，往往是第二期才补，而第二期永远排不上。

## 我们怎么切

| 取舍 | 选择 | 不做什么 |
| --- | --- | --- |
| 运行时 | Spring Boot 单体可跑，能力拆 Maven 模块 | 不拆微服务、不绑某一云厂商 |
| 编排 | LangGraph4j 图定义落库，CHAT / GRAPH 两种模式 | 不追求 Python 生态里每一个实验性库 |
| 模型 | 表 `ai_model` 按 `model_code` 建客户端 | 不读死 `spring.ai.openai.*` 当唯一来源 |
| 扩展 | Port / SPI（图谱、记忆、登录用户） | 不强制你换掉现有用户体系 |
| 产品边界 | 单租户控制台 + starter 嵌入 | 多租户、Central 发布另说 |

技术栈写进仓库 README：Java 21、Spring Boot 4.x、Spring AI、LangGraph4j。控制台是 Vue，接口在 `/api/*`。

## 和「自己包一层 OpenAI SDK」的差别

自己包 SDK 很快：发消息、收流、塞几个 Function Call。缺的是：

- Agent 配置、图版本、试跑与生产同一条链路  
- 工具目录与 MCP 同步进同一张 `ai_tool`，再绑到 Agent  
- HITL、checkpoint、运行轨迹与告警  
- 知识库 +（可选）图谱增强检索  

这些不是炫技，是「业务方敢把审批、查单、写库交给 Agent」时绕不开的件。

## 适合谁、不适合谁

**适合**：已有 Spring 业务系统；希望 Agent 配置可运营、可审计；愿意用 PostgreSQL（含 pgvector），可选 AGE 图谱。

**不适合**：只想 notebook 里快速试 Prompt；必须深度绑定某一 Python Agent 框架的全套插件。

下一篇：[半小时跑通对话与图编排](01-get-started-in-30-minutes.md)。
