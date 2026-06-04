-- ===============================================
-- 1. 初始化系统厂商数据 (2026 真实可靠配置)
-- ===============================================
INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, supported_capabilities, config_schema, is_active, priority)
VALUES
    ('openai', 'OpenAI', 'https://api.openai.com/v1',
     '["CHAT","EMBEDDING","OCR","VISION","REASONING","CODE","TOOL_CALLING"]',
     '{"modelFetch":{"enabled":true,"method":"GET","urlTemplate":"{baseUrl}/models","auth":{"type":"bearer"},"response":{"itemsPath":"data","idPath":"id","displayNamePath":"id","ownedByPath":"owned_by"}},"temperature":{"type":"float","default":0.7,"min":0,"max":2},"max_tokens":{"type":"int","default":2000,"min":1,"max":128000}}',
     TRUE, 100),

    ('anthropic', 'Anthropic Claude', 'https://api.anthropic.com/v1',
     '["CHAT","OCR","VISION","REASONING","TOOL_CALLING"]',
     '{"modelFetch":{"enabled":true,"method":"GET","urlTemplate":"{baseUrl}/models","auth":{"type":"api_key_header","headerName":"x-api-key"},"headers":{"anthropic-version":"2023-06-01"},"response":{"itemsPath":"data","idPath":"id","displayNamePath":"display_name","ownedByPath":"type"}},"temperature":{"type":"float","default":0.7,"min":0,"max":1},"max_tokens":{"type":"int","default":4000,"min":1,"max":200000}}',
     TRUE, 95),

    ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1',
     '["CHAT","CODE","REASONING"]',
     '{"modelFetch":{"enabled":true,"method":"GET","urlTemplate":"{baseUrl}/models","auth":{"type":"bearer"},"response":{"itemsPath":"data","idPath":"id","displayNamePath":"id","ownedByPath":"owned_by"}},"temperature":{"type":"float","default":1.0,"min":0,"max":2},"max_tokens":{"type":"int","default":2000,"min":1,"max":8192}}',
     TRUE, 90),

    ('glm', '智谱 AI (Zhipu)', 'https://open.bigmodel.cn/api/paas/v4',
     '["CHAT","EMBEDDING","OCR","VISION","TOOL_CALLING"]',
     '{"modelFetch":{"enabled":true,"method":"GET","urlTemplate":"{baseUrl}/models","auth":{"type":"bearer"},"response":{"itemsPath":"data","idPath":"id","displayNamePath":"id","ownedByPath":"owned_by"}},"temperature":{"type":"float","default":0.9,"min":0.01,"max":0.99},"top_p":{"type":"float","default":0.7,"min":0.01,"max":0.99}}',
     TRUE, 85),

    ('qwen', '通义千问 (DashScope)', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
     '["CHAT","EMBEDDING","OCR","VISION","RERANK","TOOL_CALLING"]',
     '{"modelFetch":{"enabled":true,"method":"GET","urlTemplate":"{baseUrl}/models","auth":{"type":"bearer"},"response":{"itemsPath":"data","idPath":"id","displayNamePath":"id","ownedByPath":"owned_by"}},"temperature":{"type":"float","default":0.8,"min":0,"max":2}}',
     TRUE, 80);

-- ===============================================
-- 2. 初始化管理员账户 (密码: admin123)
-- 注：ID 字段由于是自增，此处不再手动指定
-- ===============================================
INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
VALUES ('admin', '$2a$10$EasYxZ6ZB.YqlgDI8XnH4uuFow/KHNVnTLhXhOoBvhPTMK.FdrvEW', '系统管理员', 'admin@tolink.com', 'ADMIN', 1);

-- ===============================================
-- 3. 可选：初始化一个测试用户的 LLM 配置 (示例)
-- ===============================================
-- 假设管理员 ID 为 10000，DeepSeek 厂商 ID 为 10002
-- API Key 字段必须写入 AES-256-GCM 密文；此处不再内置明文示例配置。
