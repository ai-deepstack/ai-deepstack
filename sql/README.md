# SQL

| 脚本 | 用途 |
| --- | --- |
| `00_deepstack_bootstrap_pg.sql` | 全量建库：扩展、schema、业务表、演示种子、模板种子 |
| `01_sys_config.sql` | **运营配置中心** `sys_config` + 种子；知识库 GraphRAG 列补齐 |
| `02_agent_run_observability.sql` | 运行可观测列补齐（已有库） |
| `03_production_features.sql` | F1–F9：模型可见性、发布图、配额、HITL、告警表、sys_config、模板种子 |

新库按顺序执行；`docker compose` 已挂载 `00`–`03` 到 initdb。已有库按需补跑：

```bash
psql -U postgres -d ai_deepstack -f sql/00_deepstack_bootstrap_pg.sql
psql -U postgres -d ai_deepstack -f sql/01_sys_config.sql
psql -U postgres -d ai_deepstack -f sql/02_agent_run_observability.sql
psql -U postgres -d ai_deepstack -f sql/03_production_features.sql
```

`IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / 种子 `ON CONFLICT`，可重复执行。

JDBC 需要 `currentSchema=deepstack`。图定义草稿在 `ai_agent.graph_definition`，线上 GRAPH 用 `published_graph_definition`。

## 可选：Apache AGE

默认 compose 镜像没有 AGE。要用知识图谱时，先装与 PG 大版本匹配的 [Apache AGE](https://age.apache.org/)，再执行：

```sql
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public, deepstack;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'deepstack_kg') THEN
    PERFORM create_graph('deepstack_kg');
  END IF;
END $$;
```

业务总开关在 `sys_config`：`graph.enabled` / `memory.enabled`（`1`/`0`），控制台「系统设置」可改；yml 仅作兜底。生产相关键见 `run.redact.*` / `quota.*` / `hitl.*` / `alert.*`。

## 审计列

| 字段 | 默认 | 谁写 |
| --- | --- | --- |
| `is_del` | `0` | MyBatis-Plus `@TableLogic`，业务代码不要赋 |
| `create_time` | `CURRENT_TIMESTAMP` | 库，业务不要赋 |
| `update_time` | `CURRENT_TIMESTAMP` | 触发器，业务不要赋 |

带 `is_del` 的业务索引是 `WHERE is_del = 0` 的部分索引。

## 演示模型

```sql
UPDATE deepstack.ai_model
SET base_url = '...', api_key = '...', api_model_name = '...', enabled = 1, update_time = NOW()
WHERE model_code = 'demo-openai-chat';
```
