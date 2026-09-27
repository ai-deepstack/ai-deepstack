-- 智能体运行可观测列（已有库补齐；新库亦可重复执行）
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS agent_code VARCHAR(64);
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS orchestrate_mode SMALLINT DEFAULT 1;
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS thread_id VARCHAR(128);
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS stages JSONB;
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(32);
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS hitl_suspended_at TIMESTAMP;
ALTER TABLE deepstack.agent_workflow_run
    ADD COLUMN IF NOT EXISTS hitl_wait_ms INT;

CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_trace
    ON deepstack.agent_workflow_run (trace_id) WHERE is_del = 0 AND trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_mode_time
    ON deepstack.agent_workflow_run (orchestrate_mode, create_time DESC) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_error_code
    ON deepstack.agent_workflow_run (error_code) WHERE is_del = 0 AND error_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_agent_code
    ON deepstack.agent_workflow_run (agent_code) WHERE is_del = 0 AND agent_code IS NOT NULL;
