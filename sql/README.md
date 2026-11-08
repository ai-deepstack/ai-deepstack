# SQL

| 脚本 | 用途 |
| --- | --- |
| `00_deepstack_bootstrap_pg.sql` | 全量建库：扩展、schema、业务表、演示种子（`demo_chat` / `demo_graph`） |
| `01_sys_config.sql` | **运营配置中心** `sys_config` + 种子；知识库 GraphRAG 列补齐 |
| `02_agent_run_observability.sql` | 运行可观测列补齐（已有库） |
| `03_production_features.sql` | F1–F9：模型可见性、发布图、配额、HITL、告警表、sys_config、名称唯一 |
| `04_agent_copy_no_template.sql` | 已有库：删 `template` 列与 `tpl_*` 种子，补名称唯一索引 |
| `05_quota_hot_path_indexes.sql` | 已有库：配额热路径索引（`agent_code` / `creator_id` + `create_time`） |
| `06_agent_terminology.sql` | 已有库：表 / 列注释由「智能体」改为「Agent」（只改注释文本） |
| `07_model_custom_headers.sql` | 已有库：`ai_model.custom_headers`（模型自定义请求头 JSON） |

新库按顺序执行；`docker compose` 已挂载 `00`–`03` 到 initdb。已有库按需补跑：

```bash
psql -U postgres -d ai_deepstack -f sql/00_deepstack_bootstrap_pg.sql
psql -U postgres -d ai_deepstack -f sql/01_sys_config.sql
psql -U postgres -d ai_deepstack -f sql/02_agent_run_observability.sql
psql -U postgres -d ai_deepstack -f sql/03_production_features.sql
psql -U postgres -d ai_deepstack -f sql/04_agent_copy_no_template.sql
psql -U postgres -d ai_deepstack -f sql/05_quota_hot_path_indexes.sql
psql -U postgres -d ai_deepstack -f sql/06_agent_terminology.sql
psql -U postgres -d ai_deepstack -f sql/07_model_custom_headers.sql
```

`IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` / 种子 `ON CONFLICT`，可重复执行。

JDBC 需要 `currentSchema=deepstack`。图定义草稿在 `ai_agent.graph_definition`，线上 GRAPH 用 `published_graph_definition`。

## 可选：Apache AGE

默认 compose 镜像没有 AGE。要用知识图谱时，先装与 PG 大版本匹配的 [Apache AGE](https://age.apache.org/)，并在 `postgresql.conf`（或 `conf.d/*.conf`）配置 `shared_preload_libraries = 'age'` 后**重启** PostgreSQL，再执行：

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

应用连库用户必须是图 schema 及内部表的 owner，否则写图会报 `must be owner of table _ag_label_vertex`。如果图是用 `postgres` 建的，需要转移归属（示例用户 `ai_deepstack`）：

```sql
ALTER SCHEMA deepstack_kg OWNER TO ai_deepstack;
ALTER TABLE deepstack_kg._ag_label_vertex OWNER TO ai_deepstack;
ALTER TABLE deepstack_kg._ag_label_edge OWNER TO ai_deepstack;
ALTER SEQUENCE deepstack_kg._ag_label_vertex_id_seq OWNER TO ai_deepstack;
ALTER SEQUENCE deepstack_kg._ag_label_edge_id_seq OWNER TO ai_deepstack;
ALTER SEQUENCE deepstack_kg._label_id_seq OWNER TO ai_deepstack;
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
