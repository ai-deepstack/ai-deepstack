# 半小时跑通对话与图编排

目标：本机起依赖 → 改一条模型密钥 → 打开控制台，用种子 Agent 打通一轮对话。细节与环境变量以仓库根 [README](../../README.md) 为准；本文只保留最短路径。

## 前置

JDK 21、Maven 3.9+、Docker。前端热更新才需要 Node 20。

## 1. 起基础设施

仓库根目录：

```bash
docker compose up -d
```

会起 PostgreSQL（库 `ai_deepstack`，带 pgvector）、Redis、对象存储 OSS（S3 兼容）。新数据卷会执行 `sql/00_deepstack_bootstrap_pg.sql`（建 schema `deepstack`、表、演示数据）。

本地 OSS 与应用默认桶名可能不一致。二选一：

```bash
export DEEPSTACK_S3_DEFAULT_BUCKET=deepstack
export DEEPSTACK_KB_BUCKET=deepstack
```

或在对象存储控制台（`http://localhost:9001`，账号见 compose）建出应用配置里的桶。

## 2. 填真实模型

业务调用走表 `deepstack.ai_model`，不读 `spring.ai.openai.*`。种子 `demo-openai-chat` 的 `api_key` 是占位：

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

要用知识库时，再准备一条 `model_type = 1`（EMBEDDING）的模型，并挂到知识库的 `embedding_model_code`。CHAT 是 `0`。

## 3. 打包并启动

```bash
mvn -DskipTests -Pfrontend package
java -jar ai-deepstack-app/target/ai-deepstack-app-0.1.0.jar
```

打开 [http://localhost:8088](http://localhost:8088)，账号 `admin` / `admin123`。上线前务必改密码和 `DEEPSTACK_JWT_SECRET`。

仓库里若已有打好的静态资源，可只 `mvn -DskipTests package`。前端单独热更新：

```bash
cd ai-deepstack-web && npm ci && npm run dev
```

Vite `5173`，`/api` 代理到 `8088`。

## 4. 试对话

种子 Agent：

| agentCode | 模式 | 用途 |
| --- | --- | --- |
| `demo_chat` | CHAT | 普通对话 |
| `demo_graph` | GRAPH | 图编排示例 |

接口：

- `POST /api/chat/send`
- `POST /api/chat/stream`

请求体带 `agentCode`。控制台「对话」页选对应 Agent 即可。

## 5. 常见卡点

| 现象 | 排查 |
| --- | --- |
| 连不上库 | JDBC 固定 `currentSchema=deepstack`；检查 `DEEPSTACK_DB_*` |
| 模型调用失败 | `ai_model` 的 base_url / api_key / enabled |
| 上传知识库失败 | 桶名与 `DEEPSTACK_*_BUCKET`、OSS 是否一致 |
| 401 | 先登录拿 JWT；开发默认密钥勿用于生产 |

## 接下来

- 理解 Agent 配置与图定义：[Agent 是一等公民](02-agent-as-first-class.md)  
- 运维向配置中心：[sys-config.md](../sys-config.md)  
- 生产功能清单：[production-features.md](../production-features.md)
