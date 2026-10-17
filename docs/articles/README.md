# 技术文章系列

面向「要在 Java / Spring 里落地 Agent」的读者。写法偏工程笔记：先讲场景和取舍，再落到本仓库的模块与配置，少口号。

**先读总览：** [`../project-introduction.md`](../project-introduction.md)（必要性、优势、详细架构与配图）。

配套设计文档：[`../production-features.md`](../production-features.md)、[`../knowledge-graph-rag.md`](../knowledge-graph-rag.md)、[`../agent-observability.md`](../agent-observability.md)、[`../core-packages.md`](../core-packages.md)。本地跑通见仓库根 [`README.md`](../../README.md)。

| 序号 | 标题 | 文件 | 适合谁 |
| --- | --- | --- | --- |
| 0 | 为什么还要再做一个 Java Agent 平台 | [00-why-java-agent-platform.md](00-why-java-agent-platform.md) | 选型、架构师 |
| 1 | 半小时跑通对话与图编排 | [01-get-started-in-30-minutes.md](01-get-started-in-30-minutes.md) | 想先摸一手的人 |
| 2 | Agent 是配置实体，不是一段 Prompt | [02-agent-as-first-class.md](02-agent-as-first-class.md) | 业务建模 |
| 3 | Port / SPI：模型、记忆、图谱可替换 | [03-port-spi-architecture.md](03-port-spi-architecture.md) | 要嵌进现有系统 |
| 4 | 工具与 MCP：目录、连接、再绑定 | [04-tools-and-mcp.md](04-tools-and-mcp.md) | 接外部能力 |
| 5 | 知识库与图谱增强检索 | [05-knowledge-and-graphrag.md](05-knowledge-and-graphrag.md) | RAG / 领域知识 |
| 6 | HITL：图中途停下来等人 | [06-hitl-human-in-the-loop.md](06-hitl-human-in-the-loop.md) | 审批、确认流 |
| 7 | 上线前：脱敏、配额、版本、告警 | [07-production-hardening.md](07-production-hardening.md) | 运维 / 负责人 |
| 8 | 用 starter 嵌进自己的 Spring Boot | [08-embed-with-starter.md](08-embed-with-starter.md) | 二次集成 |

建议阅读顺序：0 → 1 → 2，再按需要挑 3–8。转载或发公众号时，文末保留仓库地址与 Apache-2.0 说明即可。
