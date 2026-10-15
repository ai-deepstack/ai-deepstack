-- ====================================================================
-- 03_production_features.sql
-- F1–F9 生产功能：轨迹脱敏配置、配额、图发布、HITL 超时、告警、模型可见性
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
-- F2 / F3 / F4：ai_agent 配额、发布图、HITL 超时
-- --------------------------------------------------------------------
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

-- 已有 GRAPH Agent：把当前草稿同步为已发布，避免上线后对话断档
UPDATE deepstack.ai_agent
   SET published_graph_definition = graph_definition,
       published_version = COALESCE(graph_version, 1)
 WHERE orchestrate_mode = 1
   AND graph_definition IS NOT NULL
   AND published_graph_definition IS NULL;

COMMENT ON COLUMN deepstack.ai_agent.published_graph_definition IS '已发布图定义；/api/chat GRAPH 只跑此字段';
COMMENT ON COLUMN deepstack.ai_agent.published_version IS '已发布版本号（与乐观锁 graph_version 独立）';
COMMENT ON COLUMN deepstack.ai_agent.quota_qps IS 'Agent 每秒请求上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_concurrency IS 'Agent 并发运行上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_daily_tokens IS 'Agent 每日 token 上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.hitl_timeout_minutes IS 'HITL 等待超时分钟；空则用全局配置';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_name_active
    ON deepstack.ai_agent (agent_name)
    WHERE is_del = 0;

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

COMMENT ON TABLE deepstack.ai_agent_graph_version IS 'Agent 已发布图版本历史';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.id IS '主键';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.agent_id IS 'Agent 主键（ai_agent.id）';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.version IS '发布版本号，同一 Agent 内递增';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.definition IS '该版本图定义 JSON（发布快照，供回滚）';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.remark IS '发布备注';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_agent_graph_version.create_time IS '创建时间（默认当前时间；业务勿赋值）';

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

COMMENT ON TABLE deepstack.agent_alert_event IS 'Agent 告警触发记录';
COMMENT ON COLUMN deepstack.agent_alert_event.id IS '主键';
COMMENT ON COLUMN deepstack.agent_alert_event.rule_code IS '规则编码：ERROR_RATE / P95 / HITL_BACKLOG / RUNNING（阈值在 sys_config）';
COMMENT ON COLUMN deepstack.agent_alert_event.agent_code IS '关联 Agent 编码；全站规则为 NULL';
COMMENT ON COLUMN deepstack.agent_alert_event.severity IS '严重级别：warn / critical 等';
COMMENT ON COLUMN deepstack.agent_alert_event.title IS '告警标题';
COMMENT ON COLUMN deepstack.agent_alert_event.detail IS '告警详情';
COMMENT ON COLUMN deepstack.agent_alert_event.fired_at IS '触发时间';
COMMENT ON COLUMN deepstack.agent_alert_event.acknowledged IS '是否已确认：1已确认 0未确认';
COMMENT ON COLUMN deepstack.agent_alert_event.create_time IS '创建时间（默认当前时间；业务勿赋值）';

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
    (1900010000000000038, 'hitl.timeout-minutes', 'HITL 全局超时分钟', '60', 'int', 'WAITING_HUMAN 超时；Agent 未单独配置时用'),
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

COMMIT;

DO $$
BEGIN
    RAISE NOTICE '03_production_features complete: model visibility, agent publish/quota, graph versions, alerts, sys_config seeds, agent name unique.';
END $$;
