-- Agent：删除 template 列与历史模板种子；名称全局唯一。
-- 已有库执行；新库已由 00 / 03 按无模板模型建表，只需补跑本脚本中的唯一索引与清种子。

BEGIN;

SET search_path TO deepstack, public;

-- 1) 删除历史公共模板种子（及绑定 / 图版本）
DELETE FROM deepstack.ai_agent_tool
 WHERE agent_id IN (
    SELECT id FROM deepstack.ai_agent
     WHERE agent_code IN ('tpl_chat', 'tpl_rag', 'tpl_graph_confirm')
 );

DELETE FROM deepstack.ai_agent_knowledge_base
 WHERE agent_id IN (
    SELECT id FROM deepstack.ai_agent
     WHERE agent_code IN ('tpl_chat', 'tpl_rag', 'tpl_graph_confirm')
 );

DELETE FROM deepstack.ai_agent_graph_version
 WHERE agent_id IN (
    SELECT id FROM deepstack.ai_agent
     WHERE agent_code IN ('tpl_chat', 'tpl_rag', 'tpl_graph_confirm')
 );

DELETE FROM deepstack.ai_agent
 WHERE agent_code IN ('tpl_chat', 'tpl_rag', 'tpl_graph_confirm');

-- 2) 丢掉模板索引与列（无兼容保留）
DROP INDEX IF EXISTS deepstack.idx_ai_agent_template;

ALTER TABLE deepstack.ai_agent
    DROP COLUMN IF EXISTS template;

-- 3) 名称唯一前先处理重名（保留 id 最小的一条，其余加后缀）
WITH dups AS (
    SELECT id,
           agent_name,
           ROW_NUMBER() OVER (PARTITION BY agent_name ORDER BY id) AS rn
    FROM deepstack.ai_agent
    WHERE is_del = 0
)
UPDATE deepstack.ai_agent a
SET agent_name = a.agent_name || ' #' || dups.id::text,
    update_time = NOW()
FROM dups
WHERE a.id = dups.id
  AND dups.rn > 1;

-- 4) 名称唯一索引（逻辑未删除范围内）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_name_active
    ON deepstack.ai_agent (agent_name)
    WHERE is_del = 0;

COMMIT;
