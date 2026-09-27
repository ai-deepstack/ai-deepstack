-- ====================================================================
-- 01_sys_config.sql
-- 运营配置中心 + 知识库 GraphRAG 列补齐
-- 前置：已执行 00_deepstack_bootstrap_pg.sql（schema deepstack 与业务表已存在）
-- 可重复执行：CREATE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / ON CONFLICT DO NOTHING
-- 主键：BIGINT 雪花 ID（与全库一致；种子脚本写死 id，运行时由 MyBatis-Plus ASSIGN_ID 生成）
-- ====================================================================
-- psql -U postgres -d ai_deepstack -f sql/01_sys_config.sql
-- ====================================================================

BEGIN;

SET search_path TO deepstack, public;

-- --------------------------------------------------------------------
-- 1. sys_config（id 雪花主键；config_key 业务唯一键；config_name 展示名）
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deepstack.sys_config (
    id           BIGINT       PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL,
    config_name  VARCHAR(128) NOT NULL,
    config_value TEXT         NOT NULL,
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'string',
    description  VARCHAR(512),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_config_config_key UNIQUE (config_key)
);

-- 旧版以 config_key 为 PK、无 id 列时补齐（仅早期草稿库）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = 'deepstack' AND table_name = 'sys_config'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'deepstack' AND table_name = 'sys_config' AND column_name = 'id'
    ) THEN
        ALTER TABLE deepstack.sys_config ADD COLUMN id BIGINT;
        WITH numbered AS (
            SELECT config_key, 1900010000000000000 + ROW_NUMBER() OVER (ORDER BY config_key) AS sid
              FROM deepstack.sys_config
        )
        UPDATE deepstack.sys_config s
           SET id = numbered.sid
          FROM numbered
         WHERE s.config_key = numbered.config_key;
        ALTER TABLE deepstack.sys_config ALTER COLUMN id SET NOT NULL;
        ALTER TABLE deepstack.sys_config DROP CONSTRAINT IF EXISTS sys_config_pkey;
        ALTER TABLE deepstack.sys_config ADD PRIMARY KEY (id);
        ALTER TABLE deepstack.sys_config
            DROP CONSTRAINT IF EXISTS uk_sys_config_config_key;
        ALTER TABLE deepstack.sys_config
            ADD CONSTRAINT uk_sys_config_config_key UNIQUE (config_key);
    END IF;
END $$;

ALTER TABLE deepstack.sys_config
    ADD COLUMN IF NOT EXISTS config_name VARCHAR(128);

UPDATE deepstack.sys_config
   SET config_name = config_key
 WHERE config_name IS NULL OR btrim(config_name) = '';

ALTER TABLE deepstack.sys_config
    ALTER COLUMN config_name SET NOT NULL;

COMMENT ON TABLE deepstack.sys_config IS '运营级系统配置（是否类 value 存 0/1）';
COMMENT ON COLUMN deepstack.sys_config.id IS '主键（雪花 ID）';
COMMENT ON COLUMN deepstack.sys_config.config_key IS '配置键（唯一）';
COMMENT ON COLUMN deepstack.sys_config.config_name IS '配置名（展示用）';
COMMENT ON COLUMN deepstack.sys_config.config_value IS '配置值（是否类为 0 或 1）';
COMMENT ON COLUMN deepstack.sys_config.value_type IS 'string / yes_no / int / json / model';
COMMENT ON COLUMN deepstack.sys_config.description IS '说明';
COMMENT ON COLUMN deepstack.sys_config.updated_at IS '更新时间（触发器刷新）';

CREATE OR REPLACE FUNCTION deepstack.set_sys_config_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sys_config_set_updated_at ON deepstack.sys_config;
CREATE TRIGGER trg_sys_config_set_updated_at
    BEFORE UPDATE ON deepstack.sys_config
    FOR EACH ROW EXECUTE FUNCTION deepstack.set_sys_config_updated_at();

-- 种子：登记 key/名称/类型；config_value 留空 → 运行时回落 yml；设置页保存后才写库覆盖
-- 冲突时只回填元数据，不覆盖已有 config_value
INSERT INTO deepstack.sys_config (id, config_key, config_name, config_value, value_type, description) VALUES
    (1900010000000000001, 'graph.enabled', '图谱总开关', '', 'yes_no', '平台图谱业务总开关'),
    (1900010000000000002, 'memory.enabled', '长期记忆总开关', '', 'yes_no', '长期记忆总开关'),
    (1900010000000000003, 'memory.graph-by-default', '记忆默认走图', '', 'yes_no', '未显式传 enableGraph 时是否默认走图'),
    (1900010000000000004, 'mcp.enabled', 'MCP 总开关', '', 'yes_no', 'MCP 总开关'),
    (1900010000000000005, 'mcp.tool-code-prefix', 'MCP 工具编码前缀', '', 'string', '同步进 ai_tool 的 code 前缀'),
    (1900010000000000006, 'tools.disclosure-mode', '工具披露模式', '', 'string', '工具披露：off / progressive'),
    (1900010000000000007, 'tools.progressive-full-below', '渐进披露全量阈值', '', 'int', 'progressive 下小允许集仍全量披露'),
    (1900010000000000008, 'tools.search-top-k', '工具搜索条数', '', 'int', 'tool_search 返回条数'),
    (1900010000000000009, 'checkpoint.enabled', 'Checkpoint 开关', '', 'yes_no', '图 Checkpoint / HITL'),
    (1900010000000000010, 'knowledge.graph.hops', '图谱扩边跳数', '', 'int', '知识库召回扩边跳数'),
    (1900010000000000011, 'knowledge.graph.max-chunks-per-extract', '建图每批 chunk 数', '', 'int', '建图每批送模型 chunk 数'),
    (1900010000000000012, 'knowledge.graph.chat-model-code', '图谱抽取模型', '', 'model', '全局默认抽取 CHAT 模型'),
    (1900010000000000013, 'knowledge.graph.graph-extra-top-k', '图扩展追加上限', '', 'int', '图扩展追加上限（空=跟 topK）'),
    (1900010000000000014, 'knowledge.recall.max-kb-concurrency', '多库召回并发', '', 'int', '多库召回并行上限'),
    (1900010000000000015, 'knowledge.recall.max-expand-concurrency', '扩边并发', '', 'int', '单库多种子 expand 上限'),
    (1900010000000000016, 'knowledge.recall.timeout-ms', '单库召回超时', '', 'int', '单库召回超时毫秒'),
    (1900010000000000017, 'llm.connect-timeout-ms', '模型连接超时', '', 'int', 'CHAT/EMBEDDING HTTP 连接超时毫秒'),
    (1900010000000000018, 'llm.read-timeout-ms', '模型读超时', '', 'int', 'CHAT/EMBEDDING HTTP 读超时毫秒'),
    (1900010000000000019, 'memory.recall.max-expand-concurrency', '记忆扩边并发', '', 'int', '长期记忆图扩边并行上限'),
    (1900010000000000020, 'mcp.bootstrap.max-concurrency', 'MCP启动建连并发', '', 'int', '启动时并行 connectAndSync 上限'),
    (1900010000000000021, 'chat.context.timeout-ms', 'CHAT拼prompt总超时', '', 'int', '画像/RAG/记忆并行拼装总超时毫秒')
ON CONFLICT (config_key) DO UPDATE SET
    config_name = EXCLUDED.config_name,
    description = EXCLUDED.description,
    value_type = EXCLUDED.value_type;

-- --------------------------------------------------------------------
-- 2. knowledge_base / knowledge_document GraphRAG 列（已有库补齐）
-- --------------------------------------------------------------------
ALTER TABLE deepstack.knowledge_base
    ADD COLUMN IF NOT EXISTS enable_graph SMALLINT DEFAULT 0;

ALTER TABLE deepstack.knowledge_base
    ADD COLUMN IF NOT EXISTS graph_model_code VARCHAR(64);

ALTER TABLE deepstack.knowledge_document
    ADD COLUMN IF NOT EXISTS graph_status SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE deepstack.knowledge_document
    ADD COLUMN IF NOT EXISTS graph_error TEXT;

CREATE INDEX IF NOT EXISTS idx_knowledge_base_enable_graph
    ON deepstack.knowledge_base (enable_graph)
    WHERE is_del = 0 AND enable_graph = 1;

CREATE INDEX IF NOT EXISTS idx_knowledge_document_graph_status
    ON deepstack.knowledge_document (knowledge_base_id, graph_status)
    WHERE is_del = 0;

COMMENT ON COLUMN deepstack.knowledge_base.enable_graph IS '是否启用图谱增强：1是 0否';
COMMENT ON COLUMN deepstack.knowledge_base.graph_model_code IS '库级图谱抽取 CHAT 模型（空则用 sys_config knowledge.graph.chat-model-code）';
COMMENT ON COLUMN deepstack.knowledge_document.graph_status IS '写图状态：0未处理 1写入中 2已写入 3失败 4跳过';
COMMENT ON COLUMN deepstack.knowledge_document.graph_error IS '写图失败原因（截断）';

COMMIT;

DO $$
BEGIN
    RAISE NOTICE '01_sys_config complete: sys_config (snowflake id + config_name) seeded; knowledge graph columns ensured.';
END $$;
