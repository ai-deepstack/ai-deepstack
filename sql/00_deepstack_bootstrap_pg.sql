-- ====================================================================
-- AI Deepstack PostgreSQL bootstrap
-- ====================================================================
-- Purpose: greenfield install for ai-deepstack-app.
--          All business tables live in schema `deepstack` (explicit deepstack.* DDL).
--          Extensions vector/pg_trgm stay in `public` (opclasses referenced as public.*).
--          JDBC: currentSchema=deepstack (entity @TableName has no schema prefix).
--
-- NOT for: migrating older product databases. This file is greenfield-only.
--
-- Prerequisites:
--   - PostgreSQL 16+ with pgvector available
--   - SUPERUSER (or equivalent) for CREATE EXTENSION
--
-- Example:
--   psql -U postgres -d ai_deepstack -f sql/00_deepstack_bootstrap_pg.sql
--
-- JDBC:
--   jdbc:postgresql://host:5432/ai_deepstack?currentSchema=deepstack
--
-- Idempotent: CREATE IF NOT EXISTS + ON CONFLICT where unique keys exist.
-- Primary keys: BIGINT snowflake IDs (MyBatis-Plus IdType.ASSIGN_ID); no SERIAL.
-- ====================================================================

BEGIN;

-- ====================================================================
-- 0. Extensions + schema
-- ====================================================================
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS deepstack;
-- Tables are created as deepstack.*; public remains for extension opclasses / types.
SET search_path TO deepstack, public;

-- ====================================================================
-- 1. ai_model
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_model (
    id              BIGINT PRIMARY KEY,
    model_code      VARCHAR(64)  NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    model_type      SMALLINT     NOT NULL DEFAULT 0,
    provider        VARCHAR(32)  NOT NULL,
    base_url        VARCHAR(256) NOT NULL,
    api_key         VARCHAR(512) NOT NULL,
    api_model_name  VARCHAR(128) NOT NULL,
    extra_json      TEXT,
    enabled         SMALLINT     DEFAULT 1,
    remark          VARCHAR(512),
    visibility      SMALLINT     NOT NULL DEFAULT 0,
    owner_id        BIGINT,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(64),
    modifier_id     BIGINT,
    modifier        VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_model_model_code UNIQUE (model_code)
);


CREATE INDEX IF NOT EXISTS idx_ai_model_type ON deepstack.ai_model (model_type, enabled) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_ai_model_provider ON deepstack.ai_model (provider, enabled) WHERE is_del = 0;

-- ====================================================================
-- 2. ai_agent
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_agent (
    id                      BIGINT PRIMARY KEY,
    agent_code              VARCHAR(64)  NOT NULL,
    agent_name              VARCHAR(128) NOT NULL,
    system_prompt           TEXT         NOT NULL,
    model_code              VARCHAR(64)  NOT NULL,
    temperature             DECIMAL(3,2) DEFAULT 0.7,
    max_tokens              INT,
    top_p                   DECIMAL(3,2),
    memory_max_messages     INT          DEFAULT 20,
    enable_memory           SMALLINT     DEFAULT 1,
    response_format         SMALLINT     DEFAULT 0,
    response_schema         TEXT,
    enabled                 SMALLINT     DEFAULT 1,
    enable_long_term_memory SMALLINT     DEFAULT 0,
    enable_graph_memory     SMALLINT     DEFAULT 0,
    orchestrate_mode        SMALLINT     NOT NULL DEFAULT 0,
    graph_definition        JSONB,
    graph_version           INT          DEFAULT 1,
    published_graph_definition JSONB,
    published_version       INT,
    template                SMALLINT     NOT NULL DEFAULT 0,
    quota_qps               INT,
    quota_concurrency       INT,
    quota_daily_tokens      INT,
    hitl_timeout_minutes    INT,
    trace_mode              SMALLINT     DEFAULT 1,
    stream_progress         SMALLINT     DEFAULT 0,
    cover_url               VARCHAR(512),
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id              BIGINT,
    creator                 VARCHAR(64),
    modifier_id             BIGINT,
    modifier                VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_agent_agent_code UNIQUE (agent_code)
);


CREATE INDEX IF NOT EXISTS idx_ai_agent_orchestrate_mode ON deepstack.ai_agent (orchestrate_mode, enabled) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_ai_agent_long_term_memory ON deepstack.ai_agent (enable_long_term_memory) WHERE is_del = 0 AND enable_long_term_memory = 1;

-- ====================================================================
-- 3. ai_tool
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_tool (
    id              BIGINT PRIMARY KEY,
    tool_code       VARCHAR(64)  NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,
    description     TEXT,
    handler_bean    VARCHAR(128),
    source_type     SMALLINT     NOT NULL DEFAULT 0,
    mcp_connection_code VARCHAR(64),
    mcp_tool_name   VARCHAR(128),
    enabled         SMALLINT     DEFAULT 1,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(64),
    modifier_id     BIGINT,
    modifier        VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_tool_tool_code UNIQUE (tool_code)
);

ALTER TABLE deepstack.ai_tool ADD COLUMN IF NOT EXISTS source_type SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE deepstack.ai_tool ADD COLUMN IF NOT EXISTS mcp_connection_code VARCHAR(64);
ALTER TABLE deepstack.ai_tool ADD COLUMN IF NOT EXISTS mcp_tool_name VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_ai_tool_mcp_conn
    ON deepstack.ai_tool (mcp_connection_code) WHERE is_del = 0 AND source_type = 1;

-- ====================================================================
-- 3a. ai_mcp_connection（MCP Server，后台动态配置）
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_mcp_connection (
    id                   BIGINT PRIMARY KEY,
    connection_code      VARCHAR(64)  NOT NULL,
    connection_name      VARCHAR(128) NOT NULL,
    transport            SMALLINT     NOT NULL,
    endpoint             VARCHAR(512),
    command              VARCHAR(256),
    args_json            TEXT,
    secret               TEXT,
    headers_json         TEXT,
    request_timeout_ms   INT          DEFAULT 30000,
    enabled              SMALLINT     NOT NULL DEFAULT 1,
    last_error           TEXT,
    is_del               SMALLINT     NOT NULL DEFAULT 0,
    creator_id           BIGINT,
    creator              VARCHAR(64),
    modifier_id          BIGINT,
    modifier             VARCHAR(64),
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_mcp_connection_code UNIQUE (connection_code)
);

CREATE INDEX IF NOT EXISTS idx_ai_mcp_connection_enabled
    ON deepstack.ai_mcp_connection (enabled) WHERE is_del = 0;


-- ====================================================================
-- 3b. ai_intent（租户级意图字典）
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_intent (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    intent_code     VARCHAR(64)  NOT NULL,
    intent_name     VARCHAR(128) NOT NULL,
    description     TEXT,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         SMALLINT     DEFAULT 1,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(64),
    modifier_id     BIGINT,
    modifier        VARCHAR(64),
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_intent_tenant_code
    ON deepstack.ai_intent (tenant_id, intent_code) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_ai_intent_tenant_enabled
    ON deepstack.ai_intent (tenant_id, enabled, sort_order) WHERE is_del = 0;


-- ====================================================================
-- 4. ai_agent_tool
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_agent_tool (
    id              BIGINT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL,
    tool_id         BIGINT       NOT NULL,
    priority        INT          NOT NULL DEFAULT 0,
    enabled         SMALLINT     DEFAULT 1,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(64),
    modifier_id     BIGINT,
    modifier        VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_agent_tool UNIQUE (agent_id, tool_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_tool_agent ON deepstack.ai_agent_tool (agent_id) WHERE is_del = 0;

-- ====================================================================
-- 5. chat_memory
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.chat_memory (
    id               BIGINT PRIMARY KEY,
    conversation_id  VARCHAR(64)  NOT NULL,
    message_type     VARCHAR(20)  NOT NULL,
    content          TEXT         NOT NULL,
    seq              INTEGER      NOT NULL,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_seq ON deepstack.chat_memory (conversation_id, seq);
CREATE INDEX IF NOT EXISTS idx_chat_memory_create_time ON deepstack.chat_memory (create_time);

-- ====================================================================
-- 5b. ai_long_term_memory（PG 主存；可选 AGE 增强）
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_long_term_memory (
    id                BIGINT       PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    agent_code        VARCHAR(64)  NOT NULL DEFAULT '*',
    category          VARCHAR(32)  NOT NULL DEFAULT 'fact',
    content           TEXT         NOT NULL,
    graph_key         VARCHAR(128),
    conversation_id   VARCHAR(64),
    entities_json     TEXT,
    is_del            SMALLINT     NOT NULL DEFAULT 0,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ltm_user_agent
    ON deepstack.ai_long_term_memory (user_id, agent_code, create_time DESC)
    WHERE is_del = 0;

CREATE INDEX IF NOT EXISTS idx_ltm_content_trgm
    ON deepstack.ai_long_term_memory USING gin (content public.gin_trgm_ops)
    WHERE is_del = 0;

-- ====================================================================
-- 6. user_prompt
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.user_prompt (
    id              BIGINT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    prompt_content  TEXT         NOT NULL,
    is_del          INTEGER     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(50),
    modifier_id     BIGINT,
    modifier        VARCHAR(50),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_prompt_user_id UNIQUE (user_id)
);

-- ====================================================================
-- 7. knowledge_base
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.knowledge_base (
    id                      BIGINT PRIMARY KEY,
    base_code               VARCHAR(64)  NOT NULL,
    base_name               VARCHAR(128) NOT NULL,
    description             TEXT,
    domain                  VARCHAR(32),
    embedding_model_code    VARCHAR(64)  NOT NULL,
    chunk_size              INTEGER      NOT NULL DEFAULT 800,
    chunk_overlap           INTEGER      NOT NULL DEFAULT 200,
    top_k                   INTEGER      NOT NULL DEFAULT 5,
    similarity_threshold    NUMERIC(4,3) DEFAULT 0.700,
    enable_graph            SMALLINT     DEFAULT 0,
    graph_model_code        VARCHAR(64),
    rag_dataset_id          VARCHAR(64),
    enabled                 SMALLINT     NOT NULL DEFAULT 1,
    is_del          INTEGER     NOT NULL DEFAULT 0,
    creator_id              BIGINT,
    creator                 VARCHAR(50),
    modifier_id             BIGINT,
    modifier                VARCHAR(50),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_base_base_code UNIQUE (base_code)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_base_domain ON deepstack.knowledge_base (domain, enabled) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_base_enable_graph ON deepstack.knowledge_base (enable_graph) WHERE is_del = 0 AND enable_graph = 1;

-- ====================================================================
-- 8. knowledge_document
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.knowledge_document (
    id                  BIGINT PRIMARY KEY,
    knowledge_base_id   BIGINT       NOT NULL,
    title               VARCHAR(256) NOT NULL,
    source_type         SMALLINT     NOT NULL,
    source_url          TEXT,
    file_key            TEXT,
    file_path           TEXT,
    file_name           VARCHAR(256),
    file_size           BIGINT,
    mime_type           VARCHAR(64),
    raw_content         TEXT,
    content_hash        CHAR(64),
    tags                VARCHAR(256),
    parse_status        SMALLINT     NOT NULL DEFAULT 0,
    embed_status        SMALLINT     NOT NULL DEFAULT 0,
    graph_status        SMALLINT     NOT NULL DEFAULT 0,
    graph_error         TEXT,
    chunk_count         INTEGER      NOT NULL DEFAULT 0,
    error_msg           TEXT,
    is_del          INTEGER     NOT NULL DEFAULT 0,
    creator_id          BIGINT,
    creator             VARCHAR(50),
    modifier_id         BIGINT,
    modifier            VARCHAR(50),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_base_status ON deepstack.knowledge_document (knowledge_base_id, embed_status) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_document_graph_status ON deepstack.knowledge_document (knowledge_base_id, graph_status) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_document_hash ON deepstack.knowledge_document (content_hash) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_document_title_trgm ON deepstack.knowledge_document USING GIN (title public.gin_trgm_ops) WHERE is_del = 0;

-- ====================================================================
-- 9. knowledge_chunk (pgvector 1024-d)
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.knowledge_chunk (
    id                  BIGINT PRIMARY KEY,
    knowledge_base_id   BIGINT       NOT NULL,
    document_id         BIGINT       NOT NULL,
    chunk_index         INTEGER      NOT NULL,
    content             TEXT         NOT NULL,
    token_count         INTEGER,
    embedding           vector(1024),
    metadata            JSONB,
    content_tsv         tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    is_del              INTEGER      NOT NULL DEFAULT 0,
    create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_doc ON deepstack.knowledge_chunk (document_id, chunk_index) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_base ON deepstack.knowledge_chunk (knowledge_base_id) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_tsv ON deepstack.knowledge_chunk USING GIN (content_tsv) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding ON deepstack.knowledge_chunk
    USING hnsw (embedding public.vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE is_del = 0;

-- ====================================================================
-- 10. ai_agent_knowledge_base
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_agent_knowledge_base (
    id                      BIGINT PRIMARY KEY,
    agent_id                BIGINT      NOT NULL,
    knowledge_base_code     VARCHAR(64) NOT NULL,
    priority                INT         NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_kb UNIQUE (agent_id, knowledge_base_code),
    CONSTRAINT uk_agent_priority UNIQUE (agent_id, priority)
);

CREATE INDEX IF NOT EXISTS idx_agent_kb_agent_id ON deepstack.ai_agent_knowledge_base (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_kb_base_code ON deepstack.ai_agent_knowledge_base (knowledge_base_code);

-- ====================================================================
-- 11. agent_workflow_run (graph runs owned by ai_agent; no agent_workflow table)
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.agent_workflow_run (
    id                  BIGINT PRIMARY KEY,
    agent_id            BIGINT NOT NULL,
    agent_code          VARCHAR(64),
    orchestrate_mode    SMALLINT     DEFAULT 1,
    trace_id            VARCHAR(64),
    thread_id           VARCHAR(128),
    graph_version       INT,
    definition_snapshot JSONB,
    conversation_id     VARCHAR(128),
    user_message        TEXT,
    result              TEXT,
    node_executions     JSONB,
    stages              JSONB,
    status              SMALLINT     DEFAULT 0,
    error_message       TEXT,
    error_code          VARCHAR(32),
    total_tokens        INT DEFAULT 0,
    duration_ms         INT DEFAULT 0,
    hitl_suspended_at   TIMESTAMP,
    hitl_wait_ms        INT,
    creator_id          BIGINT,
    create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    is_del              SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_agent ON deepstack.agent_workflow_run (agent_id) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_conversation ON deepstack.agent_workflow_run (conversation_id) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_agent_time ON deepstack.agent_workflow_run (agent_id, create_time DESC) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_status ON deepstack.agent_workflow_run (status) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_create_time ON deepstack.agent_workflow_run (create_time DESC) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_trace ON deepstack.agent_workflow_run (trace_id) WHERE is_del = 0 AND trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_agent_workflow_run_mode_time ON deepstack.agent_workflow_run (orchestrate_mode, create_time DESC) WHERE is_del = 0;

-- ====================================================================
-- 12. agent_node_type
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.agent_node_type (
    id              BIGINT PRIMARY KEY,
    type_code       VARCHAR(64)  NOT NULL,
    label           VARCHAR(128) NOT NULL,
    icon            VARCHAR(128),
    category        VARCHAR(32)  NOT NULL,
    default_props   JSONB,
    property_fields JSONB,
    max_in_ports    INT          DEFAULT -1,
    max_out_ports   INT          DEFAULT -1,
    sort_order      INT          DEFAULT 0,
    enabled         SMALLINT     DEFAULT 1,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id      BIGINT,
    creator         VARCHAR(64),
    modifier_id     BIGINT,
    modifier        VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_node_type_code ON deepstack.agent_node_type (type_code) WHERE is_del = 0;


-- ====================================================================
-- 13. ai_chat_conversation
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_chat_conversation (
    id               BIGINT PRIMARY KEY,
    conversation_id  VARCHAR(64)  NOT NULL,
    title            VARCHAR(256),
    agent_code       VARCHAR(64),
    user_id          VARCHAR(64),
    last_message_at  TIMESTAMP,
    message_count    INT          DEFAULT 0,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id       VARCHAR(64),
    creator          VARCHAR(64),
    modifier_id      VARCHAR(64),
    modifier         VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_chat_conversation_id UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_conversation_user ON deepstack.ai_chat_conversation (user_id, last_message_at DESC) WHERE is_del = 0;

-- ====================================================================
-- 14. ai_chat_card
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_chat_card (
    id               BIGINT PRIMARY KEY,
    card_id          VARCHAR(64)  NOT NULL,
    conversation_id  VARCHAR(64)  NOT NULL,
    thread_id        VARCHAR(128),
    checkpoint_id    VARCHAR(128),
    user_id          VARCHAR(64),
    card_type        VARCHAR(64)  NOT NULL,
    title            VARCHAR(256),
    payload          JSONB,
    actions          JSONB,
    status           SMALLINT     NOT NULL DEFAULT 0,
    parent_card_id   VARCHAR(64),
    expires_at       TIMESTAMP,
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id       VARCHAR(64),
    creator          VARCHAR(64),
    modifier_id      VARCHAR(64),
    modifier         VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_chat_card_card_id UNIQUE (card_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_card_conversation ON deepstack.ai_chat_card (conversation_id, create_time DESC) WHERE is_del = 0;
CREATE INDEX IF NOT EXISTS idx_ai_chat_card_user_status ON deepstack.ai_chat_card (user_id, status) WHERE is_del = 0;

-- ====================================================================
-- 15. ai_chat_card_action
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_chat_card_action (
    id                BIGINT PRIMARY KEY,
    card_id           VARCHAR(64) NOT NULL,
    action            SMALLINT    NOT NULL,
    modified_payload  JSONB,
    operator_id       VARCHAR(64),
    is_del          SMALLINT     NOT NULL DEFAULT 0,
    creator_id        VARCHAR(64),
    creator           VARCHAR(64),
    modifier_id       VARCHAR(64),
    modifier          VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_card_action_card ON deepstack.ai_chat_card_action (card_id, create_time DESC) WHERE is_del = 0;

-- ====================================================================
-- Column / table comments (all fields)
-- ====================================================================


-- ====================================================================
-- ai_user (app-layer JWT auth) — must exist before COMMENT ON
-- ====================================================================
-- ai_user (app-layer JWT auth)
-- ====================================================================
CREATE TABLE IF NOT EXISTS deepstack.ai_user (
  id BIGINT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  display_name VARCHAR(128),
  enabled SMALLINT NOT NULL DEFAULT 1,
  is_admin SMALLINT NOT NULL DEFAULT 0,
  is_del          SMALLINT     NOT NULL DEFAULT 0,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_user_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_ai_user_enabled ON deepstack.ai_user (enabled) WHERE is_del = 0;

-- ====================================================================
-- Column / table comments
-- ====================================================================

COMMENT ON TABLE deepstack.ai_intent IS '租户级意图字典（多 Agent 复用）';
COMMENT ON COLUMN deepstack.ai_intent.tenant_id IS '租户 ID；当前无租户体系时固定为 0';
COMMENT ON COLUMN deepstack.ai_intent.intent_code IS '意图编码（路由/LLM 返回值）';
COMMENT ON COLUMN deepstack.ai_intent.intent_name IS '意图显示名';

COMMENT ON TABLE deepstack.ai_model IS 'AI 模型配置（CHAT / EMBEDDING）';
COMMENT ON COLUMN deepstack.ai_model.id IS '主键';
COMMENT ON COLUMN deepstack.ai_model.model_code IS '模型编码（唯一）';
COMMENT ON COLUMN deepstack.ai_model.model_name IS '模型显示名称';
COMMENT ON COLUMN deepstack.ai_model.model_type IS '模型类型：0=CHAT 1=EMBEDDING';
COMMENT ON COLUMN deepstack.ai_model.provider IS '厂商标识，如 openai / dashscope';
COMMENT ON COLUMN deepstack.ai_model.base_url IS 'OpenAI 兼容 API Base URL';
COMMENT ON COLUMN deepstack.ai_model.api_key IS 'API 密钥（敏感）';
COMMENT ON COLUMN deepstack.ai_model.api_model_name IS '上游实际模型名，如 gpt-4o-mini';
COMMENT ON COLUMN deepstack.ai_model.extra_json IS '扩展 JSON，如 {"dimensions":1024}';
COMMENT ON COLUMN deepstack.ai_model.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_model.remark IS '备注';
COMMENT ON COLUMN deepstack.ai_model.visibility IS '可见性：0公共 1私有';
COMMENT ON COLUMN deepstack.ai_model.owner_id IS '私有模型所有者（AiUser.id）';
COMMENT ON COLUMN deepstack.ai_model.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_model.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_model.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_model.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_model.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_model.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_model.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_agent IS '智能体（一等公民）：模型/提示词/记忆/编排；GRAPH 模式图定义内嵌';
COMMENT ON COLUMN deepstack.ai_agent.id IS '主键';
COMMENT ON COLUMN deepstack.ai_agent.agent_code IS '智能体编码（唯一，对外调用标识）';
COMMENT ON COLUMN deepstack.ai_agent.agent_name IS '智能体名称';
COMMENT ON COLUMN deepstack.ai_agent.system_prompt IS '系统提示词';
COMMENT ON COLUMN deepstack.ai_agent.model_code IS '绑定的 CHAT 模型编码（ai_model.model_code）';
COMMENT ON COLUMN deepstack.ai_agent.temperature IS '采样温度';
COMMENT ON COLUMN deepstack.ai_agent.max_tokens IS '最大生成 token 数';
COMMENT ON COLUMN deepstack.ai_agent.top_p IS 'nucleus sampling top_p';
COMMENT ON COLUMN deepstack.ai_agent.memory_max_messages IS '短期记忆保留消息条数';
COMMENT ON COLUMN deepstack.ai_agent.enable_memory IS '是否启用短期记忆：1是 0否';
COMMENT ON COLUMN deepstack.ai_agent.response_format IS '响应格式：0=TEXT 1=JSON 2=JSON_SCHEMA';
COMMENT ON COLUMN deepstack.ai_agent.response_schema IS '结构化输出 JSON Schema（可选）';
COMMENT ON COLUMN deepstack.ai_agent.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_agent.enable_long_term_memory IS '是否启用长期记忆（预留）';
COMMENT ON COLUMN deepstack.ai_agent.enable_graph_memory IS '是否启用图谱记忆（预留）';
COMMENT ON COLUMN deepstack.ai_agent.orchestrate_mode IS '编排模式：0=CHAT（直连）/ 1=GRAPH（内嵌 LangGraph）';
COMMENT ON COLUMN deepstack.ai_agent.graph_definition IS 'GRAPH 模式图定义草稿 JSON（归属本 Agent）';
COMMENT ON COLUMN deepstack.ai_agent.graph_version IS '图定义草稿乐观锁版本号';
COMMENT ON COLUMN deepstack.ai_agent.published_graph_definition IS '已发布图定义；/api/chat GRAPH 只跑此字段';
COMMENT ON COLUMN deepstack.ai_agent.published_version IS '已发布版本号（与乐观锁 graph_version 独立）';
COMMENT ON COLUMN deepstack.ai_agent.template IS '是否模板：1是（不可 /api/chat 调用）0否';
COMMENT ON COLUMN deepstack.ai_agent.quota_qps IS '智能体每秒请求上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_concurrency IS '智能体并发运行上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.quota_daily_tokens IS '智能体每日 token 上限；空=不限';
COMMENT ON COLUMN deepstack.ai_agent.hitl_timeout_minutes IS 'HITL 等待超时分钟；空则用全局配置';
COMMENT ON COLUMN deepstack.ai_agent.trace_mode IS '追踪模式：0=NONE 1=RECORD 2=STREAM';
COMMENT ON COLUMN deepstack.ai_agent.stream_progress IS '是否推送节点进度 SSE：1是 0否';
COMMENT ON COLUMN deepstack.ai_agent.cover_url IS '封面图 URL；为空时前端使用默认封面';
COMMENT ON COLUMN deepstack.ai_agent.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_agent.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_agent.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_agent.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_agent.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_agent.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_agent.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_tool IS 'LLM 工具目录（LOCAL: handler_bean；MCP: mcp_*）';
COMMENT ON COLUMN deepstack.ai_tool.id IS '主键';
COMMENT ON COLUMN deepstack.ai_tool.tool_code IS '工具编码（唯一）';
COMMENT ON COLUMN deepstack.ai_tool.tool_name IS '工具名称';
COMMENT ON COLUMN deepstack.ai_tool.description IS '工具说明（供 LLM 选择）';
COMMENT ON COLUMN deepstack.ai_tool.handler_bean IS 'Spring Bean 名（source_type=0 LOCAL）';
COMMENT ON COLUMN deepstack.ai_tool.source_type IS '0=LOCAL | 1=MCP';
COMMENT ON COLUMN deepstack.ai_tool.mcp_connection_code IS 'MCP 连接编码（source_type=1）';
COMMENT ON COLUMN deepstack.ai_tool.mcp_tool_name IS '远端工具原名';
COMMENT ON COLUMN deepstack.ai_tool.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_tool.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_tool.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_tool.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_tool.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_tool.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_tool.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_tool.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_mcp_connection IS 'MCP Server 连接（后台动态配置）';
COMMENT ON COLUMN deepstack.ai_mcp_connection.id IS '主键';
COMMENT ON COLUMN deepstack.ai_mcp_connection.connection_code IS '连接编码（唯一）';
COMMENT ON COLUMN deepstack.ai_mcp_connection.connection_name IS '连接显示名';
COMMENT ON COLUMN deepstack.ai_mcp_connection.transport IS '0=SSE / 1=STDIO';
COMMENT ON COLUMN deepstack.ai_mcp_connection.endpoint IS 'SSE/HTTP 基址 URL';
COMMENT ON COLUMN deepstack.ai_mcp_connection.command IS 'STDIO 启动命令';
COMMENT ON COLUMN deepstack.ai_mcp_connection.args_json IS 'STDIO 参数 JSON 数组';
COMMENT ON COLUMN deepstack.ai_mcp_connection.secret IS 'Bearer/密钥（列表接口不回传）';
COMMENT ON COLUMN deepstack.ai_mcp_connection.headers_json IS '额外 HTTP 头 JSON 对象';
COMMENT ON COLUMN deepstack.ai_mcp_connection.request_timeout_ms IS '请求超时毫秒';
COMMENT ON COLUMN deepstack.ai_mcp_connection.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_mcp_connection.last_error IS '最近连接/同步错误';
COMMENT ON COLUMN deepstack.ai_mcp_connection.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_mcp_connection.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_mcp_connection.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_mcp_connection.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_mcp_connection.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_mcp_connection.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_mcp_connection.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_agent_tool IS '智能体与工具绑定';
COMMENT ON COLUMN deepstack.ai_agent_tool.id IS '主键';
COMMENT ON COLUMN deepstack.ai_agent_tool.agent_id IS '智能体 ID（ai_agent.id）';
COMMENT ON COLUMN deepstack.ai_agent_tool.tool_id IS '工具 ID（ai_tool.id）';
COMMENT ON COLUMN deepstack.ai_agent_tool.priority IS '优先级，数值越大越优先';
COMMENT ON COLUMN deepstack.ai_agent_tool.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_agent_tool.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_agent_tool.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_agent_tool.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_agent_tool.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_agent_tool.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_agent_tool.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_agent_tool.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.chat_memory IS '对话短期记忆消息';
COMMENT ON COLUMN deepstack.chat_memory.id IS '主键';
COMMENT ON COLUMN deepstack.chat_memory.conversation_id IS '会话 ID';
COMMENT ON COLUMN deepstack.chat_memory.message_type IS '消息类型：USER / ASSISTANT / SYSTEM / TOOL';
COMMENT ON COLUMN deepstack.chat_memory.content IS '消息正文';
COMMENT ON COLUMN deepstack.chat_memory.seq IS '会话内序号（递增）';
COMMENT ON COLUMN deepstack.chat_memory.create_time IS '创建时间（默认当前时间；业务勿赋值）';

COMMENT ON TABLE deepstack.user_prompt IS '用户级自定义提示词';
COMMENT ON COLUMN deepstack.user_prompt.id IS '主键';
COMMENT ON COLUMN deepstack.user_prompt.user_id IS '用户 ID';
COMMENT ON COLUMN deepstack.user_prompt.prompt_content IS '提示词内容';
COMMENT ON COLUMN deepstack.user_prompt.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.user_prompt.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.user_prompt.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.user_prompt.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.user_prompt.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.user_prompt.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.user_prompt.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.knowledge_base IS '知识库配置';
COMMENT ON COLUMN deepstack.knowledge_base.id IS '主键';
COMMENT ON COLUMN deepstack.knowledge_base.base_code IS '知识库编码（唯一）';
COMMENT ON COLUMN deepstack.knowledge_base.base_name IS '知识库名称';
COMMENT ON COLUMN deepstack.knowledge_base.description IS '描述';
COMMENT ON COLUMN deepstack.knowledge_base.domain IS '业务域标签';
COMMENT ON COLUMN deepstack.knowledge_base.embedding_model_code IS 'Embedding 模型编码（ai_model.model_code）';
COMMENT ON COLUMN deepstack.knowledge_base.chunk_size IS '分块大小（字符/token 近似）';
COMMENT ON COLUMN deepstack.knowledge_base.chunk_overlap IS '分块重叠长度';
COMMENT ON COLUMN deepstack.knowledge_base.top_k IS '检索返回条数';
COMMENT ON COLUMN deepstack.knowledge_base.similarity_threshold IS '相似度阈值';
COMMENT ON COLUMN deepstack.knowledge_base.enable_graph IS '是否启用图谱增强：1是 0否';
COMMENT ON COLUMN deepstack.knowledge_base.graph_model_code IS '库级图谱抽取 CHAT 模型（空则用 sys_config knowledge.graph.chat-model-code）';
COMMENT ON COLUMN deepstack.knowledge_base.rag_dataset_id IS '外部 RAG 数据集 ID（预留）';
COMMENT ON COLUMN deepstack.knowledge_base.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.knowledge_base.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.knowledge_base.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.knowledge_base.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.knowledge_base.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.knowledge_base.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.knowledge_base.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.knowledge_base.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.knowledge_document IS '知识库文档';
COMMENT ON COLUMN deepstack.knowledge_document.id IS '主键';
COMMENT ON COLUMN deepstack.knowledge_document.knowledge_base_id IS '所属知识库 ID';
COMMENT ON COLUMN deepstack.knowledge_document.title IS '文档标题';
COMMENT ON COLUMN deepstack.knowledge_document.source_type IS '来源类型：0=MANUAL 1=FILE 2=URL';
COMMENT ON COLUMN deepstack.knowledge_document.source_url IS '来源 URL';
COMMENT ON COLUMN deepstack.knowledge_document.file_key IS '对象存储 key';
COMMENT ON COLUMN deepstack.knowledge_document.file_path IS '本地路径（可选）';
COMMENT ON COLUMN deepstack.knowledge_document.file_name IS '原始文件名';
COMMENT ON COLUMN deepstack.knowledge_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN deepstack.knowledge_document.mime_type IS 'MIME 类型';
COMMENT ON COLUMN deepstack.knowledge_document.raw_content IS '解析后的原始文本';
COMMENT ON COLUMN deepstack.knowledge_document.content_hash IS '内容哈希（去重）';
COMMENT ON COLUMN deepstack.knowledge_document.tags IS '标签（逗号分隔）';
COMMENT ON COLUMN deepstack.knowledge_document.parse_status IS '解析状态码：0待解析 1解析中 2已解析 3失败';
COMMENT ON COLUMN deepstack.knowledge_document.embed_status IS '向量化状态码：0待向量化 1向量化中 2已向量化 3失败';
COMMENT ON COLUMN deepstack.knowledge_document.graph_status IS '写图状态：0未处理 1写入中 2已写入 3失败 4跳过';
COMMENT ON COLUMN deepstack.knowledge_document.graph_error IS '写图失败原因（截断）';
COMMENT ON COLUMN deepstack.knowledge_document.chunk_count IS '分块数量';
COMMENT ON COLUMN deepstack.knowledge_document.error_msg IS '失败错误信息';
COMMENT ON COLUMN deepstack.knowledge_document.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.knowledge_document.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.knowledge_document.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.knowledge_document.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.knowledge_document.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.knowledge_document.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.knowledge_document.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.knowledge_chunk IS '知识库分块与向量';
COMMENT ON COLUMN deepstack.knowledge_chunk.id IS '主键';
COMMENT ON COLUMN deepstack.knowledge_chunk.knowledge_base_id IS '所属知识库 ID';
COMMENT ON COLUMN deepstack.knowledge_chunk.document_id IS '所属文档 ID';
COMMENT ON COLUMN deepstack.knowledge_chunk.chunk_index IS '文档内分块序号（从 0 起）';
COMMENT ON COLUMN deepstack.knowledge_chunk.content IS '分块文本';
COMMENT ON COLUMN deepstack.knowledge_chunk.token_count IS '估计 token 数';
COMMENT ON COLUMN deepstack.knowledge_chunk.embedding IS '向量（pgvector，默认 1024 维）';
COMMENT ON COLUMN deepstack.knowledge_chunk.metadata IS '分块元数据 JSON';
COMMENT ON COLUMN deepstack.knowledge_chunk.content_tsv IS '全文检索 tsvector（生成列）';
COMMENT ON COLUMN deepstack.knowledge_chunk.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.knowledge_chunk.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.knowledge_chunk.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_agent_knowledge_base IS '智能体与知识库绑定';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.id IS '主键';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.agent_id IS '智能体 ID';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.knowledge_base_code IS '知识库编码';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.priority IS '检索优先级（同智能体内唯一）';
COMMENT ON COLUMN deepstack.ai_agent_knowledge_base.create_time IS '创建时间（默认当前时间；业务勿赋值）';

COMMENT ON TABLE deepstack.agent_workflow_run IS '图运行记录（归属 ai_agent，无独立 agent_workflow 表）';
COMMENT ON COLUMN deepstack.agent_workflow_run.id IS '主键';
COMMENT ON COLUMN deepstack.agent_workflow_run.agent_id IS '智能体 ID（ai_agent.id）';
COMMENT ON COLUMN deepstack.agent_workflow_run.agent_code IS '智能体编码冗余';
COMMENT ON COLUMN deepstack.agent_workflow_run.orchestrate_mode IS '编排模式：0=CHAT 1=GRAPH';
COMMENT ON COLUMN deepstack.agent_workflow_run.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN deepstack.agent_workflow_run.thread_id IS '工作流 thread ID';
COMMENT ON COLUMN deepstack.agent_workflow_run.graph_version IS '运行时图版本';
COMMENT ON COLUMN deepstack.agent_workflow_run.definition_snapshot IS '运行时定义快照';
COMMENT ON COLUMN deepstack.agent_workflow_run.conversation_id IS '关联会话 ID';
COMMENT ON COLUMN deepstack.agent_workflow_run.user_message IS '用户输入（入库前脱敏）';
COMMENT ON COLUMN deepstack.agent_workflow_run.result IS '最终输出（入库前脱敏）';
COMMENT ON COLUMN deepstack.agent_workflow_run.node_executions IS '节点执行明细 JSON';
COMMENT ON COLUMN deepstack.agent_workflow_run.stages IS 'CHAT 阶段耗时 JSON';
COMMENT ON COLUMN deepstack.agent_workflow_run.status IS '运行状态码：0运行中 1成功 2失败 3已取消 4等待人工';
COMMENT ON COLUMN deepstack.agent_workflow_run.error_message IS '失败信息';
COMMENT ON COLUMN deepstack.agent_workflow_run.error_code IS '错误分类码';
COMMENT ON COLUMN deepstack.agent_workflow_run.total_tokens IS '累计 token';
COMMENT ON COLUMN deepstack.agent_workflow_run.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN deepstack.agent_workflow_run.hitl_suspended_at IS '进入 WAITING_HUMAN 的时间';
COMMENT ON COLUMN deepstack.agent_workflow_run.hitl_wait_ms IS 'HITL 等待毫秒';
COMMENT ON COLUMN deepstack.agent_workflow_run.creator_id IS '发起人 ID';
COMMENT ON COLUMN deepstack.agent_workflow_run.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.agent_workflow_run.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';
COMMENT ON COLUMN deepstack.agent_workflow_run.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';

COMMENT ON TABLE deepstack.agent_node_type IS '工作流节点类型目录（DB 驱动）';
COMMENT ON COLUMN deepstack.agent_node_type.id IS '主键';
COMMENT ON COLUMN deepstack.agent_node_type.type_code IS '类型编码，如 llm-node';
COMMENT ON COLUMN deepstack.agent_node_type.label IS '展示名称';
COMMENT ON COLUMN deepstack.agent_node_type.icon IS '图标标识';
COMMENT ON COLUMN deepstack.agent_node_type.category IS '分类：basic / ai / tool / flow';
COMMENT ON COLUMN deepstack.agent_node_type.default_props IS '默认属性 JSON';
COMMENT ON COLUMN deepstack.agent_node_type.property_fields IS '属性表单字段定义 JSON';
COMMENT ON COLUMN deepstack.agent_node_type.max_in_ports IS '最大入边数，-1 表示不限';
COMMENT ON COLUMN deepstack.agent_node_type.max_out_ports IS '最大出边数，-1 表示不限';
COMMENT ON COLUMN deepstack.agent_node_type.sort_order IS '排序';
COMMENT ON COLUMN deepstack.agent_node_type.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.agent_node_type.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.agent_node_type.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.agent_node_type.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.agent_node_type.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.agent_node_type.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.agent_node_type.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.agent_node_type.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_chat_conversation IS '聊天会话元数据';
COMMENT ON COLUMN deepstack.ai_chat_conversation.id IS '主键';
COMMENT ON COLUMN deepstack.ai_chat_conversation.conversation_id IS '会话业务 ID（唯一）';
COMMENT ON COLUMN deepstack.ai_chat_conversation.title IS '会话标题';
COMMENT ON COLUMN deepstack.ai_chat_conversation.agent_code IS '智能体编码';
COMMENT ON COLUMN deepstack.ai_chat_conversation.user_id IS '用户 ID';
COMMENT ON COLUMN deepstack.ai_chat_conversation.last_message_at IS '最后消息时间';
COMMENT ON COLUMN deepstack.ai_chat_conversation.message_count IS '消息条数';
COMMENT ON COLUMN deepstack.ai_chat_conversation.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_chat_conversation.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_chat_conversation.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_chat_conversation.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_chat_conversation.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_chat_conversation.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_chat_conversation.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_chat_card IS '对话结构化卡片';
COMMENT ON COLUMN deepstack.ai_chat_card.id IS '主键';
COMMENT ON COLUMN deepstack.ai_chat_card.card_id IS '卡片业务 ID（唯一）';
COMMENT ON COLUMN deepstack.ai_chat_card.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN deepstack.ai_chat_card.thread_id IS '工作流 thread ID（HITL 预留）';
COMMENT ON COLUMN deepstack.ai_chat_card.checkpoint_id IS '检查点 ID（HITL 预留）';
COMMENT ON COLUMN deepstack.ai_chat_card.user_id IS '用户 ID';
COMMENT ON COLUMN deepstack.ai_chat_card.card_type IS '卡片类型（业务自定义）';
COMMENT ON COLUMN deepstack.ai_chat_card.title IS '卡片标题';
COMMENT ON COLUMN deepstack.ai_chat_card.payload IS '卡片载荷 JSON';
COMMENT ON COLUMN deepstack.ai_chat_card.actions IS '可执行动作定义 JSON';
COMMENT ON COLUMN deepstack.ai_chat_card.status IS '卡片状态码：0待处理 1已确认 2已编辑 3已拒绝 4已过期';
COMMENT ON COLUMN deepstack.ai_chat_card.parent_card_id IS '父卡片 ID（重新生成链路）';
COMMENT ON COLUMN deepstack.ai_chat_card.expires_at IS '过期时间';
COMMENT ON COLUMN deepstack.ai_chat_card.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_chat_card.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_chat_card.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_chat_card.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_chat_card.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_chat_card.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_chat_card.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_chat_card_action IS '卡片动作历史';
COMMENT ON COLUMN deepstack.ai_chat_card_action.id IS '主键';
COMMENT ON COLUMN deepstack.ai_chat_card_action.card_id IS '卡片业务 ID';
COMMENT ON COLUMN deepstack.ai_chat_card_action.action IS '动作：0=confirm 1=edit 2=reject';
COMMENT ON COLUMN deepstack.ai_chat_card_action.modified_payload IS '编辑后的载荷（edit 时）';
COMMENT ON COLUMN deepstack.ai_chat_card_action.operator_id IS '操作人 ID';
COMMENT ON COLUMN deepstack.ai_chat_card_action.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_chat_card_action.creator_id IS '创建人 ID';
COMMENT ON COLUMN deepstack.ai_chat_card_action.creator IS '创建人名称';
COMMENT ON COLUMN deepstack.ai_chat_card_action.modifier_id IS '修改人 ID';
COMMENT ON COLUMN deepstack.ai_chat_card_action.modifier IS '修改人名称';
COMMENT ON COLUMN deepstack.ai_chat_card_action.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_chat_card_action.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_user IS '应用层登录用户（JWT）';
COMMENT ON COLUMN deepstack.ai_user.id IS '主键';
COMMENT ON COLUMN deepstack.ai_user.username IS '登录名（唯一）';
COMMENT ON COLUMN deepstack.ai_user.password_hash IS '密码哈希（BCrypt）';
COMMENT ON COLUMN deepstack.ai_user.display_name IS '显示名';
COMMENT ON COLUMN deepstack.ai_user.enabled IS '是否启用：1启用 0停用';
COMMENT ON COLUMN deepstack.ai_user.is_admin IS '是否管理员：1是 0否';
COMMENT ON COLUMN deepstack.ai_user.is_del IS '逻辑删除：0正常 1已删（默认 0；业务勿赋值，由框架逻辑删除维护）';
COMMENT ON COLUMN deepstack.ai_user.create_time IS '创建时间（默认当前时间；业务勿赋值）';
COMMENT ON COLUMN deepstack.ai_user.update_time IS '更新时间（默认当前时间，行变更时由触发器自动刷新；业务勿赋值）';

COMMENT ON TABLE deepstack.ai_long_term_memory IS '长期记忆（PG 主存；可选 AGE 增强）';
COMMENT ON COLUMN deepstack.ai_long_term_memory.id IS '主键';
COMMENT ON COLUMN deepstack.ai_long_term_memory.user_id IS '用户 ID';
COMMENT ON COLUMN deepstack.ai_long_term_memory.agent_code IS '智能体编码；* 表示跨智能体';
COMMENT ON COLUMN deepstack.ai_long_term_memory.category IS '分类，如 fact';
COMMENT ON COLUMN deepstack.ai_long_term_memory.content IS '记忆正文';
COMMENT ON COLUMN deepstack.ai_long_term_memory.graph_key IS '图谱关联键（可选）';
COMMENT ON COLUMN deepstack.ai_long_term_memory.conversation_id IS '来源会话 ID';
COMMENT ON COLUMN deepstack.ai_long_term_memory.entities_json IS '实体 JSON（可选）';
COMMENT ON COLUMN deepstack.ai_long_term_memory.is_del IS '逻辑删除：0正常 1已删';
COMMENT ON COLUMN deepstack.ai_long_term_memory.create_time IS '创建时间';
COMMENT ON COLUMN deepstack.ai_long_term_memory.update_time IS '更新时间';

COMMENT ON TABLE deepstack.ai_agent_graph_version IS '智能体已发布图版本历史';
COMMENT ON TABLE deepstack.agent_alert_event IS '智能体告警触发记录';

-- ####################################################################
-- Seeds (idempotent)
-- ####################################################################

-- 8 agent node types（界面文案中文；type_code 保持英文稳定）
INSERT INTO deepstack.agent_node_type (type_code, label, icon, category, default_props, property_fields, max_in_ports, max_out_ports, sort_order, enabled) VALUES
('start-node', '开始', 'el-icon-video-play', 'basic',
 '{"outputVar":"user_message"}', '[]', 0, 1, 1, 1),
('intent-node', '意图分类', 'el-icon-cpu', 'ai',
 '{"categories":[],"prompt":"请判断用户意图，只返回类别名称","outputVar":"intent"}',
 '[{"key":"modelCode","label":"对话模型","widget":"select","required":false,"optionsSource":"CHAT_MODEL","placeholder":"选择对话模型"},{"key":"categories","label":"意图类别","widget":"select","required":true,"optionsSource":"INTENT","placeholder":"从意图字典多选"},{"key":"prompt","label":"分类提示词","widget":"textarea","required":false,"defaultValue":"请判断用户意图，只返回类别名称","placeholder":"自定义分类提示词"}]',
 -1, -1, 2, 1),
('llm-node', '大模型', 'el-icon-chat-dot-round', 'ai',
 '{"systemPrompt":"你是一个智能助手","temperature":0.7,"outputVar":"response"}',
 '[{"key":"modelCode","label":"对话模型","widget":"select","required":true,"optionsSource":"CHAT_MODEL","placeholder":"选择对话模型"},{"key":"systemPrompt","label":"系统提示词","widget":"textarea","required":true,"defaultValue":"你是一个智能助手","placeholder":"系统提示词"},{"key":"temperature","label":"温度","widget":"number","required":false,"defaultValue":0.7,"placeholder":"0.0~2.0"},{"key":"inputVars","label":"输入变量","widget":"json","required":false,"placeholder":"变量名数组"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"response","placeholder":"结果写入的变量名"}]',
 -1, 1, 3, 1),
('rag-node', '知识检索', 'el-icon-reading', 'ai',
 '{"topK":5,"similarityThreshold":0.7,"outputVar":"rag_context"}',
 '[{"key":"knowledgeBaseCodes","label":"知识库","widget":"select","required":true,"optionsSource":"KB","placeholder":"选择知识库"},{"key":"topK","label":"召回条数","widget":"number","required":false,"defaultValue":5,"placeholder":"1~50"},{"key":"similarityThreshold","label":"相似度阈值","widget":"number","required":false,"defaultValue":0.7,"placeholder":"0.0~1.0"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"rag_context","placeholder":"检索结果变量名"}]',
 -1, 1, 4, 1),
('tool-node', '工具调用', 'el-icon-s-tools', 'tool',
 '{"outputVar":"tool_result"}',
 '[{"key":"toolCode","label":"选择工具","widget":"select","required":true,"optionsSource":"TOOL","placeholder":"选择要调用的工具"},{"key":"paramMappings","label":"参数映射","widget":"json","required":false,"placeholder":"{参数名: 变量名或字面量}"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"tool_result","placeholder":"工具结果变量名"}]',
 -1, 1, 5, 1),
('condition-node', '条件分支', 'el-icon-sort', 'flow',
 '{"conditionExpression":"","outputVar":"condition_result"}',
 '[{"key":"conditionExpression","label":"节点条件（求值）","widget":"input","required":true,"placeholder":"例如：rag_context != null，结果写入输出变量"},{"key":"outputVar","label":"输出变量","widget":"input","required":false,"defaultValue":"condition_result","placeholder":"默认 condition_result；分支路由写在出边上"}]',
 -1, -1, 6, 1),
('memory-node', '长期记忆', 'el-icon-collection', 'ai',
 '{"mode":"read","enableGraph":false,"limit":10}',
 '[{"key":"mode","label":"模式","widget":"select","required":true,"options":[{"value":"read","label":"读取记忆"},{"value":"write","label":"写入记忆"}],"defaultValue":"read"},{"key":"enableGraph","label":"启用图谱","widget":"switch","required":false,"defaultValue":false},{"key":"limit","label":"返回条数","widget":"number","required":false,"defaultValue":10,"placeholder":"读模式返回条数"}]',
 -1, 1, 7, 1),
('card-gate-node', '人工确认', 'el-icon-user', 'flow',
 '{"requirePendingCards":true}',
 '[{"key":"requirePendingCards","label":"仅有待确认卡片时挂起","widget":"switch","required":false,"defaultValue":true}]',
 -1, 1, 8, 1),
('assign-node', '变量赋值', 'el-icon-edit-outline', 'flow',
 '{"variableName":"temp_var","expression":""}',
 '[{"key":"variableName","label":"变量名","widget":"input","required":true,"placeholder":"要赋值的变量名"},{"key":"expression","label":"表达式","widget":"input","required":true,"placeholder":"字面量或 ${变量名}"}]',
 -1, 1, 9, 1),
('end-node', '结束', 'el-icon-circle-close', 'basic',
 '{"outputVar":"response"}',
 '[{"key":"outputVar","label":"输出变量","widget":"input","required":false,"defaultValue":"response","placeholder":"最终输出变量名"}]',
 -1, 0, 10, 1)
ON CONFLICT DO NOTHING;

-- 已有库升级：节点显示名与属性面板文案改为中文（可重复执行）
UPDATE deepstack.agent_node_type SET label = '开始', update_time = NOW() WHERE type_code = 'start-node';
UPDATE deepstack.agent_node_type SET
  label = '意图分类',
  default_props = '{"categories":[],"prompt":"请判断用户意图，只返回类别名称","outputVar":"intent"}',
  property_fields = '[{"key":"modelCode","label":"对话模型","widget":"select","required":false,"optionsSource":"CHAT_MODEL","placeholder":"选择对话模型"},{"key":"categories","label":"意图类别","widget":"select","required":true,"optionsSource":"INTENT","placeholder":"从意图字典多选"},{"key":"prompt","label":"分类提示词","widget":"textarea","required":false,"defaultValue":"请判断用户意图，只返回类别名称","placeholder":"自定义分类提示词"}]',
  update_time = NOW()
WHERE type_code = 'intent-node';
UPDATE deepstack.agent_node_type SET
  label = '大模型',
  default_props = '{"systemPrompt":"你是一个智能助手","temperature":0.7,"outputVar":"response"}',
  property_fields = '[{"key":"modelCode","label":"对话模型","widget":"select","required":true,"optionsSource":"CHAT_MODEL","placeholder":"选择对话模型"},{"key":"systemPrompt","label":"系统提示词","widget":"textarea","required":true,"defaultValue":"你是一个智能助手","placeholder":"系统提示词"},{"key":"temperature","label":"温度","widget":"number","required":false,"defaultValue":0.7,"placeholder":"0.0~2.0"},{"key":"inputVars","label":"输入变量","widget":"json","required":false,"placeholder":"变量名数组"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"response","placeholder":"结果写入的变量名"}]',
  update_time = NOW()
WHERE type_code = 'llm-node';
UPDATE deepstack.agent_node_type SET
  label = '知识检索',
  property_fields = '[{"key":"knowledgeBaseCodes","label":"知识库","widget":"select","required":true,"optionsSource":"KB","placeholder":"选择知识库"},{"key":"topK","label":"召回条数","widget":"number","required":false,"defaultValue":5,"placeholder":"1~50"},{"key":"similarityThreshold","label":"相似度阈值","widget":"number","required":false,"defaultValue":0.7,"placeholder":"0.0~1.0"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"rag_context","placeholder":"检索结果变量名"}]',
  update_time = NOW()
WHERE type_code = 'rag-node';
UPDATE deepstack.agent_node_type SET
  label = '工具调用',
  property_fields = '[{"key":"toolCode","label":"选择工具","widget":"select","required":true,"optionsSource":"TOOL","placeholder":"选择要调用的工具"},{"key":"paramMappings","label":"参数映射","widget":"json","required":false,"placeholder":"{参数名: 变量名或字面量}"},{"key":"outputVar","label":"输出变量","widget":"input","required":true,"defaultValue":"tool_result","placeholder":"工具结果变量名"}]',
  update_time = NOW()
WHERE type_code = 'tool-node';
UPDATE deepstack.agent_node_type SET
  label = '条件分支',
  property_fields = '[{"key":"conditionExpression","label":"节点条件（求值）","widget":"input","required":true,"placeholder":"例如：rag_context != null，结果写入输出变量"},{"key":"outputVar","label":"输出变量","widget":"input","required":false,"defaultValue":"condition_result","placeholder":"默认 condition_result；分支路由写在出边上"}]',
  update_time = NOW()
WHERE type_code = 'condition-node';
INSERT INTO deepstack.agent_node_type (type_code, label, icon, category, default_props, property_fields, max_in_ports, max_out_ports, sort_order, enabled)
SELECT
  'memory-node', '长期记忆', 'el-icon-collection', 'ai',
  '{"mode":"read","enableGraph":false,"limit":10}',
  '[{"key":"mode","label":"模式","widget":"select","required":true,"options":[{"value":"read","label":"读取记忆"},{"value":"write","label":"写入记忆"}],"defaultValue":"read"},{"key":"enableGraph","label":"启用图谱","widget":"switch","required":false,"defaultValue":false},{"key":"limit","label":"返回条数","widget":"number","required":false,"defaultValue":10,"placeholder":"读模式返回条数"}]',
  -1, 1, 7, 1
WHERE NOT EXISTS (
  SELECT 1 FROM deepstack.agent_node_type WHERE type_code = 'memory-node' AND is_del = 0
);
UPDATE deepstack.agent_node_type SET
  label = '长期记忆',
  default_props = '{"mode":"read","enableGraph":false,"limit":10}',
  property_fields = '[{"key":"mode","label":"模式","widget":"select","required":true,"options":[{"value":"read","label":"读取记忆"},{"value":"write","label":"写入记忆"}],"defaultValue":"read"},{"key":"enableGraph","label":"启用图谱","widget":"switch","required":false,"defaultValue":false},{"key":"limit","label":"返回条数","widget":"number","required":false,"defaultValue":10,"placeholder":"读模式返回条数"}]',
  max_in_ports = -1,
  max_out_ports = 1,
  sort_order = 7,
  enabled = 1,
  update_time = NOW()
WHERE type_code = 'memory-node';
INSERT INTO deepstack.agent_node_type (type_code, label, icon, category, default_props, property_fields, max_in_ports, max_out_ports, sort_order, enabled)
SELECT
  'card-gate-node', '人工确认', 'el-icon-user', 'flow',
  '{"requirePendingCards":true}',
  '[{"key":"requirePendingCards","label":"仅有待确认卡片时挂起","widget":"switch","required":false,"defaultValue":true}]',
  -1, 1, 8, 1
WHERE NOT EXISTS (
  SELECT 1 FROM deepstack.agent_node_type WHERE type_code = 'card-gate-node' AND is_del = 0
);
UPDATE deepstack.agent_node_type SET
  label = '人工确认',
  default_props = '{"requirePendingCards":true}',
  property_fields = '[{"key":"requirePendingCards","label":"仅有待确认卡片时挂起","widget":"switch","required":false,"defaultValue":true}]',
  max_in_ports = -1,
  max_out_ports = 1,
  sort_order = 8,
  enabled = 1,
  update_time = NOW()
WHERE type_code = 'card-gate-node';
UPDATE deepstack.agent_node_type SET
  label = '变量赋值',
  property_fields = '[{"key":"variableName","label":"变量名","widget":"input","required":true,"placeholder":"要赋值的变量名"},{"key":"expression","label":"表达式","widget":"input","required":true,"placeholder":"字面量或 ${变量名}"}]',
  sort_order = 9,
  update_time = NOW()
WHERE type_code = 'assign-node';
UPDATE deepstack.agent_node_type SET
  label = '结束',
  property_fields = '[{"key":"outputVar","label":"输出变量","widget":"input","required":false,"defaultValue":"response","placeholder":"最终输出变量名"}]',
  sort_order = 10,
  update_time = NOW()
WHERE type_code = 'end-node';

-- Demo intents (tenant_id=0)
INSERT INTO deepstack.ai_intent (tenant_id, intent_code, intent_name, description, sort_order, enabled)
SELECT 0, v.code, v.name, v.descr, v.ord, 1
FROM (VALUES
  ('HEALTH', '健康咨询', '健康相关意图', 10),
  ('SPORT', '运动健身', '运动相关意图', 20),
  ('SLEEP', '睡眠休息', '睡眠相关意图', 30),
  ('GENERAL', '通用闲聊', '兜底/通用意图', 100)
) AS v(code, name, descr, ord)
WHERE NOT EXISTS (
  SELECT 1 FROM deepstack.ai_intent i
  WHERE i.tenant_id = 0 AND i.intent_code = v.code AND i.is_del = 0
);

-- Demo CHAT model (OpenAI-compatible placeholder - replace api_key before use)
INSERT INTO deepstack.ai_model (
    model_code, model_name, model_type, provider,
    base_url, api_key, api_model_name, extra_json,
    enabled, remark, creator, create_time, update_time
) VALUES (
    'demo-openai-chat',
    'Demo OpenAI-Compatible Chat',
    0,
    'openai',
    'https://api.openai.com/v1',
    'sk-REPLACE_ME',
    'gpt-4o-mini',
    NULL,
    1,
    'ai-deepstack-app placeholder; set a real api_key (any OpenAI-compatible endpoint works via base_url)',
    'system',
    NOW(),
    NOW()
)
ON CONFLICT (model_code) DO NOTHING;

-- Demo Agents: CHAT + GRAPH（图定义内嵌在 ai_agent，无独立 agent_workflow 表）
INSERT INTO deepstack.ai_agent (
    agent_code, agent_name, system_prompt, model_code,
    temperature, max_tokens, top_p, memory_max_messages, enable_memory,
    response_format, enabled, enable_long_term_memory, enable_graph_memory,
    orchestrate_mode, graph_definition, graph_version, trace_mode, stream_progress,
    creator, create_time, update_time
)
SELECT
    'demo_chat',
    'Demo Chat',
    'You are the AI Deepstack demo assistant. Answer clearly and concisely. Replace the demo model api_key before production use.',
    m.model_code,
    0.70, 2048, 0.90, 20, 1,
    0, 1, 0, 0,
    0, NULL, 1, 1, 0,
    'system', NOW(), NOW()
FROM ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

INSERT INTO deepstack.ai_agent (
    agent_code, agent_name, system_prompt, model_code,
    temperature, max_tokens, top_p, memory_max_messages, enable_memory,
    response_format, enabled, enable_long_term_memory, enable_graph_memory,
    orchestrate_mode, graph_definition, graph_version,
    published_graph_definition, published_version, template,
    trace_mode, stream_progress,
    creator, create_time, update_time
)
SELECT
    'demo_graph',
    'Demo Graph Agent',
    'You are the AI Deepstack demo assistant (GRAPH mode).',
    m.model_code,
    0.70, 2048, 0.90, 20, 1,
    0, 1, 0, 0,
    1,
    '{
      "nodes": [
        {"id": "node_start", "type": "start-node", "text": "Start", "properties": {"outputVar": "user_message"}},
        {"id": "node_llm", "type": "llm-node", "text": "LLM", "properties": {"systemPrompt": "You are a helpful AI assistant for the AI Deepstack demo.", "temperature": 0.7, "outputVar": "response"}},
        {"id": "node_end", "type": "end-node", "text": "End", "properties": {"outputVar": "response"}}
      ],
      "edges": [
        {"id": "e1", "sourceNodeId": "node_start", "targetNodeId": "node_llm", "properties": {}},
        {"id": "e2", "sourceNodeId": "node_llm", "targetNodeId": "node_end", "properties": {}}
      ],
      "variables": {}
    }'::jsonb,
    1,
    '{
      "nodes": [
        {"id": "node_start", "type": "start-node", "text": "Start", "properties": {"outputVar": "user_message"}},
        {"id": "node_llm", "type": "llm-node", "text": "LLM", "properties": {"systemPrompt": "You are a helpful AI assistant for the AI Deepstack demo.", "temperature": 0.7, "outputVar": "response"}},
        {"id": "node_end", "type": "end-node", "text": "End", "properties": {"outputVar": "response"}}
      ],
      "edges": [
        {"id": "e1", "sourceNodeId": "node_start", "targetNodeId": "node_llm", "properties": {}},
        {"id": "e2", "sourceNodeId": "node_llm", "targetNodeId": "node_end", "properties": {}}
      ],
      "variables": {}
    }'::jsonb,
    1, 0,
    1, 1,
    'system', NOW(), NOW()
FROM ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

-- ====================================================================
-- ai_user table created earlier (before COMMENT section)


-- BCrypt for password admin123 (verified)
INSERT INTO deepstack.ai_user (id, username, password_hash, display_name, enabled, is_admin)
VALUES (1900000000000000001, 'admin', '$2b$10$/WY0JPezX.KvOJ.s6e/DhuSqJK55caut4zxKgm8TWdXIgFWT2m6Wa', 'Admin', 1, 1)
ON CONFLICT (username) DO NOTHING;


-- 通用业务确认卡（HITL）
INSERT INTO deepstack.ai_tool (tool_code, tool_name, description, handler_bean, enabled)
VALUES (
  'propose_generic_confirm',
  '通用确认卡',
  '需要用户确认后再执行的操作时调用（确认、提交、删除等）。只发出确认卡，不执行副作用；用户确认后再由后续工具落地。',
  'proposeGenericConfirmTool',
  1
)
ON CONFLICT (tool_code) DO NOTHING;


-- ====================================================================
-- F3 / F5：图版本历史、告警事件表
-- ====================================================================
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

CREATE INDEX IF NOT EXISTS idx_ai_agent_template
    ON deepstack.ai_agent (template)
    WHERE is_del = 0 AND template = 1;

CREATE INDEX IF NOT EXISTS idx_ai_model_visibility_owner
    ON deepstack.ai_model (visibility, owner_id)
    WHERE is_del = 0;

-- F7：公共模板种子（不可 /api/chat；复制后启用）
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
FROM ai_model m
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
FROM ai_model m
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
FROM ai_model m
WHERE m.model_code = 'demo-openai-chat'
ON CONFLICT (agent_code) DO NOTHING;

-- sys_config / 可观测列 / 生产补齐见 01、02、03（compose 会按序执行）

-- ====================================================================
-- Done
-- ====================================================================
DO $$
DECLARE
    v_tables TEXT;
BEGIN
    SELECT string_agg(tablename, ', ' ORDER BY tablename)
      INTO v_tables
      FROM pg_tables
     WHERE schemaname = 'deepstack';
    RAISE NOTICE 'deepstack bootstrap complete. tables: %', v_tables;
    RAISE NOTICE 'seeds: agent_node_type, ai_model(demo-openai-chat), ai_agent(demo_chat, demo_graph, tpl_*), ai_user(admin)';
    RAISE NOTICE 'next: sql/01_sys_config.sql + 02 + 03; set api_key on demo-openai-chat; JDBC currentSchema=deepstack';
END $$;

-- ====================================================================
-- Audit: update_time auto-refresh（Java 业务代码无需赋值）
-- ====================================================================
CREATE OR REPLACE FUNCTION deepstack.set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'ai_model', 'ai_agent', 'ai_tool', 'ai_mcp_connection', 'ai_agent_tool', 'user_prompt',
        'knowledge_base', 'knowledge_document', 'knowledge_chunk',
        'agent_workflow_run', 'agent_node_type',
        'ai_chat_conversation', 'ai_chat_card', 'ai_chat_card_action', 'ai_user',
        'ai_long_term_memory'
    ]
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_set_update_time ON deepstack.%I', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_set_update_time BEFORE UPDATE ON deepstack.%I '
            'FOR EACH ROW EXECUTE FUNCTION deepstack.set_update_time()', t, t);
    END LOOP;
END $$;


COMMIT;
