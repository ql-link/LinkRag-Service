-- ===============================================
-- 1. 初始化系统厂商数据 (2026 真实可靠配置)
-- ===============================================
INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, supported_models, config_schema, is_active, priority)
VALUES
    ('openai', 'OpenAI', 'https://api.openai.com/v1',
     '{"gpt-4o":["CHAT","OCR","VISION"], "gpt-4-turbo":["CHAT"], "o1-preview":["REASONING"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":2}, "max_tokens":{"type":"int","default":2000,"min":1,"max":128000}}',
     TRUE, 100),

    ('claude', 'Anthropic Claude', 'https://api.anthropic.com/v1',
     '{"claude-3-5-sonnet":["CHAT","OCR"], "claude-3-opus":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":1}, "max_tokens":{"type":"int","default":4000,"min":1,"max":200000}}',
     TRUE, 95),

    ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1',
     '{"deepseek-v3":["CHAT"], "deepseek-coder":["CHAT","CODE"]}',
     '{"temperature":{"type":"float","default":1.0,"min":0,"max":2}, "max_tokens":{"type":"int","default":2000,"min":1,"max":8192}}',
     TRUE, 90),

    ('glm', '智谱 AI (Zhipu)', 'https://open.bigmodel.cn/api/paas/v4',
     '{"glm-4v":["CHAT","VISION"], "glm-4-plus":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.9,"min":0.01,"max":0.99}, "top_p":{"type":"float","default":0.7,"min":0.01,"max":0.99}}',
     TRUE, 85),

    ('aliyun', '通义千问 (DashScope)', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
     '{"qwen-max":["CHAT"], "qwen-plus":["CHAT"], "qwen-turbo":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.8,"min":0,"max":2}}',
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
INSERT INTO llm_user_config (user_id, provider_id, provider_type, provider_name, config_name, api_key, model_name, capability, is_default)
VALUES (10000, 10002, 'deepseek', 'DeepSeek', '我的测试配置', 'sk-xxxxxxxxxxxx', 'deepseek-v3', 'CHAT', TRUE);

-- ===============================================
-- 4. 文件上传相关旧数据留档
-- 来源：2026-04-29 从远端 tolink_rag_db 导出
-- 说明：
-- - 这些数据用于删库重建后按需恢复文件上传相关记录。
-- - document_parsed_file 已按新表结构转换，旧解析产物字段不再写入。
-- - knowledge_file_config 已退出 MySQL 主链路，旧值仅在注释中留档：
--   id=10000, max_size_bytes=10485760, allowed_suffixes=["txt","md","pdf","docx"], updated_by=10001
-- ===============================================

INSERT INTO document_original_file
    (id, dataset_id, user_id, original_filename, file_suffix, file_size, content_type, bucket_name, object_key, file_url, upload_status, is_upload_success, failure_reason, created_at, updated_at)
VALUES
    (10000, 10003, 10002, 'mq-smoke.md', 'md', 16, 'application/octet-stream', 'rag-raw', '10002/10003/2026/04/23/mq-smoke.md', 'http://tolink-service:8080/api/v1/internal/knowledge-files/10000/content?taskId=edfe532b-5519-4959-8aa3-4b397a8b5cc9', 'success', 1, NULL, '2026-04-23 12:07:45', '2026-04-23 12:07:51'),
    (10001, 10003, 10002, 'mq-smoke-2.md', 'md', 16, 'application/octet-stream', 'rag-raw', '10002/10003/2026/04/23/mq-smoke-2.md', 'http://tolink-service:8080/api/v1/internal/knowledge-files/10001/content?taskId=527f6c88-eec2-427b-9e4f-93f17586c68b', 'success', 1, NULL, '2026-04-23 12:49:59', '2026-04-23 12:50:02'),
    (10002, 10003, 10002, '技术实现文档模板-实体文件.md', 'md', 6522, 'application/octet-stream', 'rag-raw', '10002/10003/2026/04/23/技术实现文档模板-实体文件.md', 'http://tolink-service:8080/api/v1/internal/knowledge-files/10002/content?taskId=e1c04864-5a97-427f-9b38-b6fffd7f53a2', 'success', 1, NULL, '2026-04-23 12:50:44', '2026-04-23 12:50:56'),
    (10004, 10006, 10004, 'demo_final.txt', 'txt', 22, 'text/plain', 'rag-raw', 'original/user-10004/dataset-10006/2026/04/28/10004/demo_final.txt', 'http://tolink-service:8080/api/v1/internal/files/10004/content', 'success', 1, NULL, '2026-04-28 11:29:14', '2026-04-28 11:29:15'),
    (10005, 10007, 10005, 'tolink_mq_migration_smoke_1777376449.txt', 'txt', 46, 'text/plain', 'rag-raw', 'original/user-10005/dataset-10007/2026/04/28/10005/tolink_mq_migration_smoke_1777376449.txt', 'http://tolink-service:8080/api/v1/internal/files/10005/content', 'success', 1, NULL, '2026-04-28 11:39:33', '2026-04-28 11:39:37'),
    (10006, 10007, 10005, 'tolink_python_receive_1777385005.txt', 'txt', 43, 'text/plain', 'rag-raw', 'original/user-10005/dataset-10007/2026/04/28/10006/tolink_python_receive_1777385005.txt', 'http://tolink-service:8080/api/v1/internal/files/10006/content', 'success', 1, NULL, '2026-04-28 14:02:08', '2026-04-28 14:02:09');

INSERT INTO document_parsed_file
    (id, document_original_file_id, dataset_id, user_id, latest_parse_task_id, original_filename, parse_count, created_at, updated_at)
VALUES
    (10000, 10000, 10003, 10002, NULL, 'mq-smoke.md', 1, '2026-04-23 12:07:52', '2026-04-23 12:07:52'),
    (10001, 10001, 10003, 10002, NULL, 'mq-smoke-2.md', 1, '2026-04-23 12:50:03', '2026-04-23 12:50:03'),
    (10002, 10002, 10003, 10002, NULL, '技术实现文档模板-实体文件.md', 1, '2026-04-23 12:50:58', '2026-04-23 12:50:58');

INSERT INTO document_parse_log
    (id, task_id, document_original_file_id, dataset_id, user_id, trigger_mode, task_status, failure_reason, dispatch_retry_count, last_dispatch_error, last_dispatched_at, parse_started_at, parse_finished_at, parse_duration_ms, created_at, updated_at)
VALUES
    (1, '2efda759-5aea-49a6-883d-34f018a57f87', 10004, 10006, 10004, 'upload_auto', 'created', NULL, 0, NULL, '2026-04-28 19:30:37', NULL, NULL, NULL, NULL, NULL),
    (10000, '779d6b40-fe63-4c47-a27e-8eafca99512e', 10005, 10007, 10005, 'upload_auto', 'created', NULL, 0, NULL, '2026-04-28 19:41:02', NULL, NULL, NULL, '2026-04-28 11:39:40', '2026-04-28 11:39:43'),
    (10001, '6c3490d0-1014-4af0-b3a7-66b402da3833', 10006, 10007, 10005, 'upload_auto', 'created', NULL, 0, NULL, '2026-04-28 22:03:32', NULL, NULL, NULL, '2026-04-28 14:02:10', '2026-04-28 14:02:13');
