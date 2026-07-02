-- ===============================================
-- 1. LLM 厂商与模型目录
-- 完整 LLM 厂商与模型目录种子统一维护在 scripts/db/seed_llm_providers.sql。
-- 本文件不再内置轻量 LLM 目录，避免与全量种子分叉。
-- ===============================================

-- ===============================================
-- 1.2 初始化系统预设（可选；LinkRag 系统兜底配置，自带平台 Key）
-- 注：api_key 必须填 AES-256-GCM 加密后的密文（密钥见 tolink.llm.api-key.secret）；
--     明文会在真正调用 LLM 时 decrypt 失败。故此处给注释示例，运维替换密文后再启用。
-- ===============================================
-- INSERT INTO llm_system_preset (provider_id, provider_type, model_name, capability, protocol, api_base_url, api_key, is_active, is_default)
-- SELECT id, 'linkrag', 'linkrag-chat', 'CHAT', 'openai', 'https://api.linkrag.local/v1/chat/completions', '<加密后的平台Key密文>', TRUE, TRUE
-- FROM llm_system_provider WHERE provider_type = 'linkrag';

-- ===============================================
-- 2. 初始化管理员账户 (密码: admin123)
-- 注：ID 字段由于是自增，此处不再手动指定
-- ===============================================
INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
VALUES ('admin', '$2a$10$EasYxZ6ZB.YqlgDI8XnH4uuFow/KHNVnTLhXhOoBvhPTMK.FdrvEW', '系统管理员', 'admin@tolink.com', 'ADMIN', 1);

-- ===============================================
-- 3. 测试用户 LLM 自配配置
-- 示例配置依赖正式模型目录，请先执行 scripts/db/seed_llm_providers.sql 后再按需手工写入。
-- ===============================================
