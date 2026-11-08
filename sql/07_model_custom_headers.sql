-- ====================================================================
-- 07. ai_model.custom_headers（模型自定义请求头）
-- 已有库补跑；新库由 00_deepstack_bootstrap_pg.sql 直接建列。
-- ====================================================================

BEGIN;

ALTER TABLE deepstack.ai_model
    ADD COLUMN IF NOT EXISTS custom_headers TEXT;

COMMENT ON COLUMN deepstack.ai_model.custom_headers IS '自定义请求头 JSON 对象，如 {"x-opencode-session":"abc"}';

COMMIT;
