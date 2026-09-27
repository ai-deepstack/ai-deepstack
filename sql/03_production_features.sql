-- ====================================================================
-- 03_production_features.sql
-- F1–F9 生产功能：轨迹脱敏配置、配额、图发布、HITL 超时、告警、模板、模型可见性
-- 前置：00_deepstack_bootstrap_pg.sql、01_sys_config.sql
-- 可重复执行：ADD COLUMN IF NOT EXISTS / ON CONFLICT DO NOTHING
-- ====================================================================
-- psql -U postgres -d ai_deepstack -f sql/03_production_features.sql
-- ====================================================================

BEGIN;

SET search_path TO deepstack, public;

-- --------------------------------------------------------------------
-- F9：ai_model 可见性（0=公共 1=私有），owner_id 默认 creator_id
-- --------------------------------------------------------------------
ALTER TABLE deepstack.ai_model
    ADD COLUMN IF NOT EXISTS visibility SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE deepstack.ai_model
    ADD COLUMN IF NOT EXISTS owner_id BIGINT;

UPDATE deepstack.ai_model
   SET owner_id = creator_id
 WHERE owner_id IS NULL AND creator_id IS NOT NULL;

COMMENT ON COLUMN deepstack.ai_model.visibility IS '可见性：0公共 1私有';
COMMENT ON COLUMN deepstack.ai_model.owner_id IS '私有模型所有者（AiUser.id）';

CREATE INDEX IF NOT EXISTS idx_ai_model_visibility_owner
    ON deepstack.ai_model (visibility, owner_id)
    WHERE is_del = 0;

-- --------------------------------------------------------------------
-- F9：ai_user 管理员标记（无多租户角色体系时用）
-- --------------------------------------------------------------------
ALTER TABLE deepstack.ai_user
    ADD COLUMN IF NOT EXISTS is_admin SMALLINT NOT NULL DEFAULT 0;

UPDATE deepstack.ai_user
   SET is_admin = 1
 WHERE username = 'admin' AND (is_admin IS NULL OR is_admin = 0);

COMMENT ON COLUMN deepstack.ai_user.is_admin IS '是否管理员：1是 0否（可改公共模型与看全部私有密钥脱敏）';

-- --------------------------------------------------------------------
-- F2 / F3 / F4 / F7：ai_agent 配额、发布图、HITL 超时、模板
-- --------------------------------------------------------------------
ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS template SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS published_graph_definition JSONB;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS published_version INT;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS quota_qps INT;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS quota_concurrency INT;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS quota_daily_tokens INT;

ALTER TABLE deepstack.ai_agent
    ADD COLUMN IF NOT EXISTS hitl_timeout_minutes INT;

-- 已有 GRAPH 智能体：把当前草稿同步为已发布，避免上线后对话断档
UPDATE deepstack.ai_agent
   SET published_graph_definition = graph_definition,
       published_version = COALESCE(graph_version, 1)
 WHERE orchestrate_mode = 1
   AND graph_definition IS NOT NULL
   AND published_graph_definition IS NULL;

COMMENT ON COLUMN deepstack.ai_agent.template IS '是否模板：1是（不可 /api/chat 调用）0否';
COMMENT ON COLUMN deepstack.ai_agent.published_graph_definition IS '已发布图定义；/api/chat GRAPH 只跑此字段';
COMMENT ON COLUMN deepstack.ai_agent.published_version IS '已发布版本号（与乐观锁 graph_version 独立）';
COMMENT ON COLUMN deepstack.ai_agent.quota_qps IS '智能体每秒请求上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_concurrency IS '智能体并发运行上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_daily_tokens IS '智能体每日 token 上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.hitl_timeout_minutes IS 'HITL 等待超时分钟；空则用全局配置';

CREATE INDEX IF NOT EXISTS idx_ai_agent_template
    ON deepstack.ai_agent (template)
    WHERE is_del = 0 AND template = 1;

-- --------------------------------------------------------------------
-- F3：图版本历史（发布快照，供回滚）
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deepstack.ai_agent_graph_version (
    id                BIGINT PRIMARY KEY,
    agent_id          BIGINT       NOT NULL,
    version           INT          NOT NULL,
    definition        JSONB        NOT NULL,
    remark            VARCHAR(256),
    creator_id        BIGINT,
    creator           VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_graph_version UNIQUE (agent_id, version)
);

CREATE INDEX IF NOT EXISTS idx_agent_graph_version_agent
    ON deepstack.ai_agent_graph_version (agent_id, version DESC);

COMMENT ON TABLE deepstack.ai_agent_graph_version IS '智能体已发布图版本历史';

-- --------------------------------------------------------------------
-- F5：告警事件（规则阈值在 sys_config；触发记录落此表）
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deepstack.agent_alert_event (
    id              BIGINT PRIMARY KEY,
    rule_code       VARCHAR(64)  NOT NULL,
    agent_code      VARCHAR(64),
    severity        VARCHAR(16)  NOT NULL DEFAULT 'warn',
    title           VARCHAR(256) NOT NULL,
    detail          TEXT,
    fired_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged    SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_alert_fired
    ON deepstack.agent_alert_event (fired_at DESC);

COMMENT ON TABLE deepstack.agent_alert_event IS '智能体告警触发记录';

-- --------------------------------------------------------------------
-- sys_config 种子（F1/F2/F4/F5）
-- --------------------------------------------------------------------
INSERT INTO deepstack.sys_config (id, config_key, config_name, config_value, value_type, description) VALUES
    (1900010000000000030, 'run.redact.enabled', '轨迹脱敏开关', '1', 'yes_no', '写入运行表前是否截断与遮盖敏感内容'),
    (1900010000000000031, 'run.redact.max-user-message', '用户消息保留长度', '2000', 'int', 'user_message 最大字符数'),
    (1900010000000000032, 'run.redact.max-result', '结果保留长度', '4000', 'int', 'result 最大字符数'),
    (1900010000000000033, 'run.redact.max-node-io', '节点输入输出保留长度', '1000', 'int', 'node_executions 单字段最大字符数'),
    (1900010000000000034, 'quota.global.concurrency', '全站并发上限', '50', 'int', '进程内同时 RUNNING 上限；0=不限'),
    (1900010000000000035, 'quota.account.qps', '账号 QPS', '10', 'int', '登录用户每秒请求上限；0=不限'),
    (1900010000000000036, 'quota.account.concurrency', '账号并发', '5', 'int', '登录用户同时运行上限；0=不限'),
    (1900010000000000037, 'quota.account.daily-tokens', '账号每日 token', '0', 'int', '登录用户每日 token 上限；0=不限'),
    (1900010000000000038, 'hitl.timeout-minutes', 'HITL 全局超时分钟', '60', 'int', 'WAITING_HUMAN 超时；智能体未单独配置时用'),
    (1900010000000000039, 'alert.enabled', '告警开关', '1', 'yes_no', '是否启用告警扫描'),
    (1900010000000000040, 'alert.webhook-url', '告警 Webhook', '', 'string', '触发后 POST JSON；空则仅落库+日志'),
    (1900010000000000041, 'alert.error-rate-threshold', '错误率阈值', '0.5', 'string', '近窗口失败率，如 0.3 表示 30%'),
    (1900010000000000042, 'alert.p95-ms-threshold', 'P95 延迟阈值毫秒', '30000', 'int', '超过则告警'),
    (1900010000000000043, 'alert.hitl-backlog-threshold', 'HITL 积压阈值', '20', 'int', '等待人工条数超过则告警'),
    (1900010000000000044, 'alert.running-threshold', '在跑数阈值', '80', 'int', 'runningExecutions 超过则告警'),
    (1900010000000000045, 'alert.window-minutes', '告警统计窗口分钟', '15', 'int', '错误率/延迟统计窗口')
ON CONFLICT (config_key) DO UPDATE SET
    config_name = EXCLUDED.config_name,
    description = EXCLUDED.description,
    value_type = EXCLUDED.value_type,
    config_value = CASE
        WHEN deepstack.sys_config.config_value IS NULL OR btrim(deepstack.sys_config.config_value) = ''
            THEN EXCLUDED.config_value
        ELSE deepstack.sys_config.config_value
    END;

-- --------------------------------------------------------------------
-- F7：公共模板种子（复制后启用；不可 /api/chat）
-- --------------------------------------------------------------------
INSERT INTO deepstack.ai_agent (
    id, agent_code, agent_name, system_prompt, model_code,
    temperature, max_tokens, top_p, memory_max_messages, enable_memory,
    response_format, enabled, orchestrate_mode, graph_definition, graph_version,
    published_graph_definition, published_version, template,
    trace_mode, stream_progress, creator, create_time, update_time
)
SELECT
    1900010000000000101,
    'tpl_chat',
    '模板 · 纯对话',
    'You are a helpful assistant. Answer clearly and concisely.',
    m.model_code,
    0.70, 2048, 0.90, 20, 1,
    0, 0, 0, NULL, 1,
    NULL, NULL, 1,
    1, 0, 'system', NOW(), NOW()
FROM deepstack.ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

INSERT INTO deepstack.ai_agent (
    id, agent_code, agent_name, system_prompt, model_code,
    temperature, max_tokens, top_p, memory_max_messages, enable_memory,
    response_format, enabled, orchestrate_mode, graph_definition, graph_version,
    published_graph_definition, published_version, template,
    trace_mode, stream_progress, creator, create_time, update_time
)
SELECT
    1900010000000000102,
    'tpl_rag',
    '模板 · 对话+知识库',
    'You are a helpful assistant with knowledge-base grounding. Prefer cited facts when available; say when unsure.',
    m.model_code,
    0.50, 2048, 0.90, 20, 1,
    0, 0, 0, NULL, 1,
    NULL, NULL, 1,
    1, 0, 'system', NOW(), NOW()
FROM deepstack.ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

INSERT INTO deepstack.ai_agent (
    id, agent_code, agent_name, system_prompt, model_code,
    temperature, max_tokens, top_p, memory_max_messages, enable_memory,
    response_format, enabled, orchestrate_mode, graph_definition, graph_version,
    published_graph_definition, published_version, template,
    trace_mode, stream_progress, creator, create_time, update_time
)
SELECT
    1900010000000000103,
    'tpl_graph_confirm',
    '模板 · 图编排+确认卡',
    'You are a GRAPH agent that asks for human confirmation before finishing.',
    m.model_code,
    0.70, 2048, 0.90, 20, 1,
    0, 0, 1,
    '{
      "nodes": [
        {"id": "node_start", "type": "start-node", "text": "Start", "properties": {"outputVar": "user_message"}},
        {"id": "node_llm", "type": "llm-node", "text": "LLM", "properties": {"systemPrompt": "Summarize the user request briefly.", "temperature": 0.5, "outputVar": "summary"}},
        {"id": "node_gate", "type": "card-gate-node", "text": "确认", "properties": {"title": "请确认后继续", "contentVar": "summary", "outputVar": "confirmed"}},
        {"id": "node_end", "type": "end-node", "text": "End", "properties": {"outputVar": "summary"}}
      ],
      "edges": [
        {"id": "e1", "sourceNodeId": "node_start", "targetNodeId": "node_llm", "properties": {}},
        {"id": "e2", "sourceNodeId": "node_llm", "targetNodeId": "node_gate", "properties": {}},
        {"id": "e3", "sourceNodeId": "node_gate", "targetNodeId": "node_end", "properties": {}}
      ],
      "variables": {}
    }'::jsonb,
    1, NULL, NULL, 1,
    1, 1, 'system', NOW(), NOW()
FROM deepstack.ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

COMMIT;

DO $$
BEGIN
    RAISE NOTICE '03_production_features complete: model visibility, agent publish/quota/template, graph versions, alerts, sys_config seeds, tpl_* templates.';
END $$;
