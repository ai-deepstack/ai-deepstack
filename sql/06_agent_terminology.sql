-- Agent 术语统一：把库里已有的表 / 列注释从「智能体」改为「Agent」。
-- 只改注释文本，不动结构与数据；已有库执行本脚本，新库由 00/01/03 直接建出。
-- 可重复执行。

COMMENT ON TABLE deepstack.ai_agent IS 'Agent（一等公民）：模型/提示词/记忆/编排；GRAPH 模式图定义内嵌';
COMMENT ON COLUMN deepstack.ai_agent.agent_code IS 'Agent 编码（唯一，对外调用标识）';
COMMENT ON COLUMN deepstack.ai_agent.agent_name IS 'Agent 名称';
COMMENT ON COLUMN deepstack.ai_agent.quota_qps IS 'Agent 每秒请求上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_concurrency IS 'Agent 并发运行上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_daily_tokens IS 'Agent 每日 token 上限；空=不限';
COMMENT ON TABLE deepstack.ai_agent_tool IS 'Agent 与工具绑定';
COMMENT ON COLUMN deepstack.ai_agent_tool.agent_id IS 'Agent ID（ai_agent.id）';
COMMENT ON TABLE deepstack.ai_agent_knowledge_base IS 'Agent 与知识库绑定';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.agent_id IS 'Agent ID';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.priority IS '检索优先级（同 Agent 内唯一）';
COMMENT ON COLUMN deepstack.agent_workflow_run.agent_id IS 'Agent ID（ai_agent.id）';
COMMENT ON COLUMN deepstack.agent_workflow_run.agent_code IS 'Agent 编码冗余';
COMMENT ON COLUMN deepstack.ai_chat_conversation.agent_code IS 'Agent 编码';
COMMENT ON COLUMN deepstack.ai_long_term_memory.agent_code IS 'Agent 编码；* 表示跨 Agent';
COMMENT ON TABLE deepstack.ai_agent_graph_version IS 'Agent 已发布图版本历史';
COMMENT ON TABLE deepstack.agent_alert_event IS 'Agent 告警触发记录';
