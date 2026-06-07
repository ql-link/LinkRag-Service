-- ===============================================
-- 1. 初始化系统厂商数据（厂商表瘦身：去 supported_models / config_schema）
-- ===============================================
INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, is_active, priority)
VALUES
    ('openai', 'OpenAI', 'https://api.openai.com/v1', TRUE, 100),
    ('claude', 'Anthropic Claude', 'https://api.anthropic.com/v1', TRUE, 95),
    ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1', TRUE, 90),
    ('glm', '智谱 AI (Zhipu)', 'https://open.bigmodel.cn/api/paas/v4', TRUE, 85),
    ('aliyun', '通义千问 (DashScope)', 'https://dashscope.aliyuncs.com/compatible-mode/v1', TRUE, 80);

-- ===============================================
-- 1.1 初始化厂商模型能力目录（原 supported_models JSON 拆为行，一模型多能力=多行）
-- provider_id 用子查询按 provider_type 关联，避免硬编码自增 ID
-- ===============================================
INSERT INTO llm_provider_model (provider_id, model_name, capability)
SELECT p.id, m.model_name, m.capability
FROM llm_system_provider p
JOIN (
    SELECT 'openai'   AS provider_type, 'gpt-4o'            AS model_name, 'CHAT'      AS capability UNION ALL
    SELECT 'openai',   'gpt-4o',            'OCR'       UNION ALL
    SELECT 'openai',   'gpt-4o',            'VISION'    UNION ALL
    SELECT 'openai',   'gpt-4-turbo',       'CHAT'      UNION ALL
    SELECT 'openai',   'o1-preview',        'REASONING' UNION ALL
    SELECT 'claude',   'claude-3-5-sonnet', 'CHAT'      UNION ALL
    SELECT 'claude',   'claude-3-5-sonnet', 'OCR'       UNION ALL
    SELECT 'claude',   'claude-3-opus',     'CHAT'      UNION ALL
    SELECT 'deepseek', 'deepseek-v3',       'CHAT'      UNION ALL
    SELECT 'deepseek', 'deepseek-coder',    'CHAT'      UNION ALL
    SELECT 'deepseek', 'deepseek-coder',    'CODE'      UNION ALL
    SELECT 'glm',      'glm-4v',            'CHAT'      UNION ALL
    SELECT 'glm',      'glm-4v',            'VISION'    UNION ALL
    SELECT 'glm',      'glm-4-plus',        'CHAT'      UNION ALL
    SELECT 'aliyun',   'qwen-max',          'CHAT'      UNION ALL
    SELECT 'aliyun',   'qwen-plus',         'CHAT'      UNION ALL
    SELECT 'aliyun',   'qwen-turbo',        'CHAT'
) m ON m.provider_type = p.provider_type;

-- ===============================================
-- 1.2 初始化系统预设（可选；自带平台 Key，注册时复制进用户配置表，开箱即用）
-- 注：api_key 必须填 AES-256-GCM 加密后的密文（密钥见 tolink.llm.api-key.secret）；
--     明文会在真正调用 LLM 时 decrypt 失败。故此处给注释示例，运维替换密文后再启用。
-- ===============================================
-- INSERT INTO llm_system_preset (provider_id, model_name, capability, api_key, is_active)
-- SELECT id, 'deepseek-v3', 'CHAT', '<加密后的平台Key密文>', TRUE
-- FROM llm_system_provider WHERE provider_type = 'deepseek';

-- ===============================================
-- 2. 初始化管理员账户 (密码: admin123)
-- 注：ID 字段由于是自增，此处不再手动指定
-- ===============================================
INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
VALUES ('admin', '$2a$10$EasYxZ6ZB.YqlgDI8XnH4uuFow/KHNVnTLhXhOoBvhPTMK.FdrvEW', '系统管理员', 'admin@tolink.com', 'ADMIN', 1);

-- ===============================================
-- 3. 可选：初始化一个测试用户的 LLM 自配配置 (示例)
-- ===============================================
INSERT INTO llm_user_config (user_id, provider_id, provider_type, api_key, api_base_url, model_name, capability, is_active, is_default, is_system_preset)
SELECT u.id, p.id, 'deepseek', 'sk-xxxxxxxxxxxx', 'https://api.deepseek.com/v1', 'deepseek-v3', 'CHAT', TRUE, TRUE, FALSE
FROM sys_user u, llm_system_provider p
WHERE u.username = 'admin' AND p.provider_type = 'deepseek';
