-- 配额热路径索引：配额校验在每次请求入口按「今日 + Agent / 账号」聚合 token 与并发数，
-- 原先只有 (agent_id, create_time) 与 (create_time)，按 agent_code / creator_id 过滤时用不上。
-- 已有库执行；新库已由 00 建出同样的索引。

CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_code_time
    ON deepstack.agent_workflow_run (agent_code, create_time DESC) WHERE is_del = 0;

CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_creator_time
    ON deepstack.agent_workflow_run (creator_id, create_time DESC) WHERE is_del = 0;
