# 用 starter 嵌进自己的 Spring Boot

参考单体 `ai-deepstack-app` 适合演示与内网控制台。已有业务工程时，用 `ai-deepstack-starter` 聚合依赖，在宿主里提供数据源与用户 SPI。

## 1. 安装到本地仓库

在 DeepStack 仓库根：

```bash
mvn -DskipTests install
```

## 2. 宿主依赖

```xml
<dependency>
  <groupId>org.deepstack.ai</groupId>
  <artifactId>ai-deepstack-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

版本号以仓库当前为准。

## 3. 宿主要提供的

| 项 | 说明 |
| --- | --- |
| DataSource | JDBC 指向已执行 bootstrap 的库；`currentSchema=deepstack`（或与 SQL 一致） |
| SQL | 至少 `sql/00_deepstack_bootstrap_pg.sql`；生产功能加 `03` 等 |
| 组件扫描 | `org.deepstack.ai`（或等价 `@Import` / AutoConfiguration） |
| 用户 SPI | 实现 `AppUserProfileLoader`；没有实现时 kernel 用演示加载器 |
| 对象存储 OSS | `deepstack.s3.*` 或环境变量 `DEEPSTACK_S3_*`（S3 兼容协议） |

Redis、MCP、图谱、记忆等按需开环境变量（见根 README 配置表）。

## 4. 常用 API 前缀

`/api/auth`、`/api/chat`、`/api/agents`、`/api/models`、`/api/tools`、`/api/mcp/connections`、`/api/kb`、`/api/intents`、`/api/chat-cards`。

网关鉴权可与宿主统一：要么沿用 DeepStack JWT，要么在网关验完后注入与 `AppUserProfileLoader` 一致的用户标识。

## 5. 前端控制台

两种用法：

1. **继续用 app 的静态资源** — 适合内网运维台与 API 同域。  
2. **只嵌 API** — 自研前端；下拉用各资源 `GET /api/.../options`（`value` / `label`），列表用 `/page`。雪花 Long 在 JSON 里按字符串处理，避免 JS 精度丢失。

表单控件若对标本仓库控制台，可参考 `ai-deepstack-web` 的 `Prop*` 组件约定（仅作实现参考，不是强制依赖）。

## 6. 集成检查单

- [ ] 库表与演示数据（或自有种子）就绪  
- [ ] 至少一条可用 CHAT 模型；知识库再加 EMBEDDING  
- [ ] 用户 SPI 返回的 id 与配额、轨迹一致  
- [ ] 生产关闭演示账号默认密码；密钥走配置中心或环境变量  
- [ ] 限流、脱敏、HITL 超时按 [07](07-production-hardening.md) 过一遍  

## 边界

Starter **不**等于多租户 SaaS。租户、计费、独立域名在宿主做。也不要在未评估的情况下把 management 端点暴露到公网。

系列索引：[README.md](README.md)。开篇回顾：[为什么还要再做一个 Java Agent 平台](00-why-java-agent-platform.md)。
