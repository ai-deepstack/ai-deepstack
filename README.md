# AI DeepStack

用 Spring AI 和 LangGraph4j 搭智能体：对话、图编排、工具（含 MCP）、知识库 RAG。仓库是 Maven 多模块，`ai-deepstack-app` 是可直接跑的单体（JWT + Vue 控制台）。

Java 21，Spring Boot 4.1.1，Spring AI 2.0.1，LangGraph4j 1.8.x。

## 本地跑起来

需要 JDK 21、Maven 3.9+、Docker。前端单独开发时再加 Node 20。

```bash
docker compose up -d
```

会起 PostgreSQL（库 `ai_deepstack`，镜像带 pgvector）、Redis、MinIO（API `9000`，控制台 `9001`）。新数据卷会跑 `sql/00_deepstack_bootstrap_pg.sql`：建 schema `deepstack`、业务表、演示数据。

compose 只建了桶 `deepstack`，应用默认桶是 `ai-deepstack` / `ai-deepstack-kb`。二选一：

```bash
# 与 compose 对齐
export DEEPSTACK_S3_DEFAULT_BUCKET=deepstack
export DEEPSTACK_KB_BUCKET=deepstack
```

或在 MinIO 控制台（`http://localhost:9001`，账号 `deepstack` / `deepstack123`）建出应用配置里的两个桶。

已有 PostgreSQL 时：

```bash
psql -U postgres -d ai_deepstack -f sql/00_deepstack_bootstrap_pg.sql
```

脚本可重复执行。说明见 `[sql/README.md](sql/README.md)`。

业务调用走表 `deepstack.ai_model`，不读 `spring.ai.openai.*`。种子模型 `demo-openai-chat` 的 `api_key` 是占位，先改成真实 OpenAI 兼容接口：

```sql
UPDATE deepstack.ai_model
SET
  base_url = 'https://your-endpoint/v1',
  api_key = 'your-api-key',
  api_model_name = 'gpt-4o-mini',
  enabled = 1,
  update_time = NOW()
WHERE model_code = 'demo-openai-chat';
```

知识库还要一条 `model_type = 1`（EMBEDDING）的模型，并把 `knowledge_base.embedding_model_code` 指过去。CHAT 是 `0`。

```bash
mvn -DskipTests -Pfrontend package
java -jar ai-deepstack-app/target/ai-deepstack-app-0.1.0.jar
```

打开 [http://localhost:8088](http://localhost:8088)，账号 `admin` / `admin123`。上线前改密码和 `DEEPSTACK_JWT_SECRET`。

不重新打前端、仓库里已有 `ai-deepstack-app/src/main/resources/static` 时，可以只 `mvn -DskipTests package`。前端热更新：

```bash
cd ai-deepstack-web
npm ci
npm run dev
```

Vite 在 `5173`，`/api` 代理到 `8088`。

种子智能体：`demo_chat`（对话）、`demo_graph`（图）。对话接口是 `POST /api/chat/send` 和 `POST /api/chat/stream`，请求体带 `agentCode`。

## 配置

JDBC 固定 `currentSchema=deepstack`。默认用 P6Spy 打单行 SQL（`spy.properties`）。关掉就把 `spring.datasource` 改回 `jdbc:postgresql://` 和 `org.postgresql.Driver`。


| 变量                                       | 默认                                    | 用途                    |
| ---------------------------------------- | ------------------------------------- | --------------------- |
| `DEEPSTACK_DB_HOST` / `PORT` / `NAME`    | `localhost` / `5432` / `ai_deepstack` | PostgreSQL            |
| `DEEPSTACK_DB_USER` / `PASSWORD`         | `postgres`                            | 账号                    |
| `DEEPSTACK_REDIS_HOST` / `PORT`          | `localhost` / `6379`                  | Redis                 |
| `DEEPSTACK_REDIS_PASSWORD`               | 空                                     | 可选                    |
| `DEEPSTACK_JWT_SECRET`                   | 开发占位                                  | 生产必须换                 |
| `DEEPSTACK_JWT_EXPIRE_HOURS`             | `72`                                  | Token 有效期             |
| `DEEPSTACK_S3_ENDPOINT`                  | `http://127.0.0.1:9000`               | S3 Endpoint           |
| `DEEPSTACK_S3_ACCESS_KEY` / `SECRET_KEY` | `deepstack` / `deepstack123`          | 访问密钥                  |
| `DEEPSTACK_S3_DEFAULT_BUCKET`            | `ai-deepstack`                        | 默认桶                   |
| `DEEPSTACK_S3_PATH_STYLE`                | `true`                                | 本地 MinIO 用 path-style |
| `DEEPSTACK_KB_BUCKET`                    | `ai-deepstack-kb`                     | 知识库文档桶                |
| `DEEPSTACK_MCP_ENABLED`                  | `true`                                | MCP 运行时               |
| `DEEPSTACK_TOOLS_DISCLOSURE_MODE`        | `off`                                 | `off` 或 `progressive` |
| `DEEPSTACK_GRAPH_ENABLED`                | `false`                               | Apache AGE 知识图谱       |
| `DEEPSTACK_MEMORY_ENABLED`               | `false`                               | 长期记忆                  |
| `DEEPSTACK_CHECKPOINT_ENABLED`           | `true`                                | 图 checkpoint / HITL   |
| `SERVER_PORT`                            | `8088`                                | HTTP                  |


状态和类型在库里是数字码，接口会带对应 `*Name`。码值见 `org.deepstack.ai.kernel.enums`。

下拉框用各资源的 `GET /api/.../options`（只返回 `value` / `label`），列表页用 `/page`。

## 模块

包根 `org.deepstack.ai`。分层习惯见 [`docs/core-packages.md`](docs/core-packages.md)。运营配置中心见 [`docs/sys-config.md`](docs/sys-config.md)。知识库图谱增强见 [`docs/knowledge-graph-rag.md`](docs/knowledge-graph-rag.md)。


| 模块                       | 做什么                                   |
| ------------------------ | ------------------------------------- |
| `ai-deepstack-kernel`    | 响应包装、异常、登录 SPI、分页、公共枚举                |
| `ai-deepstack-aimodel`   | 模型配置 CRUD                             |
| `ai-deepstack-infra`     | 按 `model_code` 建 Chat / Embedding 客户端 |
| `ai-deepstack-agent`     | 智能体、图定义、试跑、工具绑定、意图                    |
| `ai-deepstack-tool`      | 工具目录、MCP 连接与同步                        |
| `ai-deepstack-knowledge` | 知识库、文档、pgvector                       |
| `ai-deepstack-graph`     | 图谱 SPI；`graph-age` 是 Apache AGE 实现    |
| `ai-deepstack-memory`    | 长期记忆 SPI；`memory-pg` 是 PG 实现          |
| `ai-deepstack-chat`      | 对话、短记忆、结构化输出                          |
| `ai-deepstack-card`      | 对话卡片、HITL                             |
| `ai-deepstack-runtime`   | 图编译与执行                                |
| `ai-deepstack-starter`   | 给宿主工程依赖的聚合包                           |
| `ai-deepstack-app`       | 参考单体                                  |
| `ai-deepstack-web`       | Vue 3 控制台                             |


图定义存在 `ai_agent.graph_definition`（`orchestrate_mode = 1`），没有单独的 workflow 表。MCP 连接在 `ai_mcp_connection`，同步进 `ai_tool`（`source_type = 1`）；智能体绑的是工具，不是连接。

## 嵌进别的 Spring Boot 工程

```bash
mvn -DskipTests install
```

```xml
<dependency>
  <groupId>org.deepstack.ai</groupId>
  <artifactId>ai-deepstack-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

宿主要提供 DataSource、执行 bootstrap SQL、扫描 `org.deepstack.ai`（或等价），并实现 `AppUserProfileLoader`（没有实现时 kernel 会落演示加载器）。对象存储配 `deepstack.s3.*`。

常用前缀：`/api/auth`、`/api/chat`、`/api/agents`、`/api/models`、`/api/tools`、`/api/mcp/connections`、`/api/kb`、`/api/intents`、`/api/chat-cards`。

## 生产前

- 库已执行 bootstrap
- `ai_model` 里 CHAT（以及要用的 EMBEDDING）填了真实 `base_url` / `api_key`
- 换掉 JWT 密钥和 `admin` 密码
- S3 桶名与配置一致，不要用仓库里的本地密钥
- `management` 只暴露需要的端点（默认 `health`、`info`）

## License

[Apache License 2.0](LICENSE)