-- =============================================================
-- toLink-Service：LLM 厂商与模型目录种子数据（精简主力厂商版）
-- 生成日期：2026-07-02
-- 来源：本地 Docker MySQL tolink_rag_db.llm_system_provider / llm_provider_model
-- 厂商：17 个；厂商模型能力记录：84 条；默认上架模型能力：84 条；LinkRag 系统预设：6 条。
-- 策略：只保留国内/国外主力厂商与 LinkRag 系统厂商；每个厂商最多保留 5 个当前主推模型。
-- LinkRag 只注册系统厂商，不写入 llm_provider_model；其模型只写入 llm_system_preset。
-- 全新环境首次写入系统预设前需先设置加密平台 Key：
--   SET @linkrag_system_preset_api_key = '<AES-256-GCM 加密后的平台 Key 密文>';
-- display_name 保留主版本号（如 2.5 / 4.8），不保留发布日期或快照号。
-- =============================================================

USE tolink_rag_db;

START TRANSACTION;

-- 1. 厂商基本信息：只维护主力厂商白名单，并下架历史非白名单厂商。
DROP TEMPORARY TABLE IF EXISTS tmp_seed_llm_providers;

CREATE TEMPORARY TABLE tmp_seed_llm_providers (
    provider_type VARCHAR(32) NOT NULL PRIMARY KEY,
    provider_name VARCHAR(64) NOT NULL,
    icon_url VARCHAR(512),
    icon_object_key VARCHAR(256),
    api_base_url VARCHAR(512) NOT NULL,
    default_protocol VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 50
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_llm_providers (
    provider_type, provider_name, icon_url, icon_object_key, api_base_url, default_protocol, is_active, priority
)
VALUES
    ('aliyun', 'Aliyun', 'http://localhost:39000/tolink-public/providerIcon/aliyun.svg', 'providerIcon/aliyun.svg', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'openai', TRUE, 99),
    ('claude', 'Anthropic', 'http://localhost:39000/tolink-public/providerIcon/claude.svg', 'providerIcon/claude.svg', 'https://api.anthropic.com', 'anthropic', TRUE, 99),
    ('deepseek', 'DeepSeek', 'http://localhost:39000/tolink-public/providerIcon/deepseek.svg', 'providerIcon/deepseek.svg', 'https://api.deepseek.com/v1', 'openai', TRUE, 99),
    ('gemini', 'Google', 'http://localhost:39000/tolink-public/providerIcon/gemini.svg', 'providerIcon/gemini.svg', 'https://generativelanguage.googleapis.com/v1beta', 'google', TRUE, 99),
    ('glm', 'ZHIPU-AI', 'http://localhost:39000/tolink-public/providerIcon/glm.svg', 'providerIcon/glm.svg', 'https://open.bigmodel.cn/api/paas/v4', 'openai', TRUE, 99),
    ('moonshot', 'Moonshot', 'http://localhost:39000/tolink-public/providerIcon/moonshot.svg', 'providerIcon/moonshot.svg', 'https://api.moonshot.cn/v1', 'openai', TRUE, 99),
    ('openai', 'OpenAI', 'http://localhost:39000/tolink-public/providerIcon/openai.svg', 'providerIcon/openai.svg', 'https://api.openai.com/v1', 'openai', TRUE, 99),
    ('xai', 'xAI', 'http://localhost:39000/tolink-public/providerIcon/xai.svg', 'providerIcon/xai.svg', 'https://api.x.ai/v1', 'openai', TRUE, 99),
    ('hunyuan', 'HunYuan', 'http://localhost:39000/tolink-public/providerIcon/hunyuan.svg', 'providerIcon/hunyuan.svg', 'https://api.hunyuan.cloud.tencent.com/v1', 'openai', TRUE, 50),
    ('jina', 'Jina', 'http://localhost:39000/tolink-public/providerIcon/jina.svg', 'providerIcon/jina.svg', 'https://api.jina.ai/v1', 'jina', TRUE, 50),
    ('volcengine', 'VolcEngine', 'http://localhost:39000/tolink-public/providerIcon/volcengine.svg', 'providerIcon/volcengine.svg', 'https://ark.cn-beijing.volces.com/api/v3', 'openai', TRUE, 50),
    ('linkrag', 'LinkRag', 'http://localhost:39000/tolink-public/providerIcon/linkrag.png', 'providerIcon/linkrag.png', 'https://api.siliconflow.cn/v1', 'openai', TRUE, 100),
    ('minimax', 'MiniMax', 'http://localhost:39000/tolink-public/providerIcon/minimax.svg', 'providerIcon/minimax.svg', 'https://api.minimaxi.com/', 'openai', TRUE, 98),
    ('mimo', 'Xiaomi MiMo Token Plan', 'http://localhost:39000/tolink-public/providerIcon/mimo.svg', 'providerIcon/mimo.svg', 'https://token-plan-cn.xiaomimimo.com/v1', 'openai', TRUE, 50),
    ('huggingface', 'HuggingFace', 'http://localhost:39000/tolink-public/providerIcon/huggingface.svg', 'providerIcon/huggingface.svg', 'https://router.huggingface.co/v1', 'openai', TRUE, 98),
    ('openrouter', 'OpenRouter', 'http://localhost:39000/tolink-public/providerIcon/openrouter.svg', 'providerIcon/openrouter.svg', 'https://openrouter.ai/api/v1', 'openai', TRUE, 98),
    ('siliconflow', 'SiliconFlow', 'http://localhost:39000/tolink-public/providerIcon/siliconflow.svg', 'providerIcon/siliconflow.svg', 'https://api.siliconflow.cn/v1', 'openai', TRUE, 50);

INSERT INTO llm_system_provider (
    provider_type, provider_name, icon_url, icon_object_key, api_base_url, default_protocol, is_active, priority
)
SELECT provider_type, provider_name, icon_url, icon_object_key, api_base_url, default_protocol, is_active, priority
FROM tmp_seed_llm_providers
ON DUPLICATE KEY UPDATE
    provider_name    = VALUES(provider_name),
    icon_url         = VALUES(icon_url),
    icon_object_key  = VALUES(icon_object_key),
    api_base_url     = VALUES(api_base_url),
    default_protocol = VALUES(default_protocol),
    is_active        = VALUES(is_active),
    priority         = VALUES(priority),
    updated_at       = CURRENT_TIMESTAMP;

UPDATE llm_system_provider sp
LEFT JOIN tmp_seed_llm_providers seed
  ON seed.provider_type COLLATE utf8mb4_unicode_ci = sp.provider_type COLLATE utf8mb4_unicode_ci
SET sp.is_active = FALSE,
    sp.updated_at = CURRENT_TIMESTAMP
WHERE seed.provider_type IS NULL;

-- 2. 模型能力目录：只保留当前主推模型；未列入当前种子的历史模型能力会被下架。
DROP TEMPORARY TABLE IF EXISTS tmp_seed_llm_provider_models;

CREATE TEMPORARY TABLE tmp_seed_llm_provider_models (
    provider_type VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(64),
    capability VARCHAR(32) NOT NULL,
    protocol VARCHAR(32),
    api_base_url VARCHAR(512),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (provider_type, model_name, capability)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_seed_llm_provider_models (provider_type, model_name, display_name, capability, protocol, api_base_url, is_active)
VALUES
    ('aliyun', 'qwen3-asr-flash', 'Qwen 3 ASR Flash', 'ASR', 'dashscope', 'https://dashscope.aliyuncs.com/api/v1', TRUE),
    ('aliyun', 'qwen3-max', 'Qwen 3 Max', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ('aliyun', 'qwen3-rerank', 'Qwen 3 Rerank', 'RERANK', 'dashscope', 'https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank', TRUE),
    ('aliyun', 'qwen3.5-flash', 'Qwen 3.5 Flash', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ('aliyun', 'qwen3.5-flash', 'Qwen 3.5 Flash', 'VISION', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ('aliyun', 'qwen3.5-plus', 'Qwen 3.5 Plus', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ('aliyun', 'qwen3.5-plus', 'Qwen 3.5 Plus', 'VISION', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ('claude', 'claude-haiku-4-5-20251001', 'Claude Haiku 4.5', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-haiku-4-5-20251001', 'Claude Haiku 4.5', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-opus-4-7', 'Claude Opus 4.7', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-opus-4-7', 'Claude Opus 4.7', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-opus-4-8', 'Claude Opus 4.8', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-opus-4-8', 'Claude Opus 4.8', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-sonnet-4-5-20250929', 'Claude Sonnet 4.5', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-sonnet-4-5-20250929', 'Claude Sonnet 4.5', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-sonnet-4-6', 'Claude Sonnet 4.6', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('claude', 'claude-sonnet-4-6', 'Claude Sonnet 4.6', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ('deepseek', 'deepseek-v4-flash', 'DeepSeek V4 Flash', 'CHAT', 'openai', 'https://api.deepseek.com/v1/chat/completions', TRUE),
    ('deepseek', 'deepseek-v4-pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://api.deepseek.com/v1/chat/completions', TRUE),
    ('gemini', 'gemini-2.5-flash', 'Gemini 2.5 Flash', 'CHAT', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ('gemini', 'gemini-2.5-flash', 'Gemini 2.5 Flash', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ('gemini', 'gemini-2.5-pro', 'Gemini 2.5 Pro', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ('gemini', 'gemini-3-pro-preview', 'Gemini 3 Pro Preview', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ('gemini', 'gemini-embedding-001', 'Gemini Embedding', 'EMBEDDING', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ('glm', 'embedding-3', 'GLM Embedding 3', 'EMBEDDING', 'openai', 'https://open.bigmodel.cn/api/paas/v4/embeddings', TRUE),
    ('glm', 'glm-5', 'GLM 5', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ('glm', 'glm-5-turbo', 'GLM 5 Turbo', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ('glm', 'glm-5v-turbo', 'GLM 5V Turbo', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ('glm', 'glm-asr-2512', 'GLM ASR', 'ASR', 'openai', 'https://open.bigmodel.cn/api/paas/v4/audio/transcriptions', TRUE),
    ('moonshot', 'kimi-k2-thinking', 'Kimi K2 Thinking', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ('moonshot', 'kimi-k2-thinking-turbo', 'Kimi K2 Thinking Turbo', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ('moonshot', 'kimi-k2.6', 'Kimi K2.6', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ('moonshot', 'kimi-k2.6', 'Kimi K2.6', 'VISION', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ('moonshot', 'kimi-latest', 'Kimi', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ('openai', 'gpt-5-mini', 'GPT 5 Mini', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5-mini', 'GPT 5 Mini', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.1-chat-latest', 'GPT 5.1 Chat', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.1-chat-latest', 'GPT 5.1 Chat', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.4', 'GPT 5.4', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.4', 'GPT 5.4', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.5', 'GPT 5.5', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'gpt-5.5', 'GPT 5.5', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ('openai', 'text-embedding-3-large', 'OpenAI Embedding 3 Large', 'EMBEDDING', 'openai', 'https://api.openai.com/v1/embeddings', TRUE),
    ('xai', 'grok-3-fast', 'Grok 3 Fast', 'CHAT', 'openai', 'https://api.x.ai/v1/chat/completions', TRUE),
    ('xai', 'grok-4', 'Grok 4', 'CHAT', 'openai', 'https://api.x.ai/v1/chat/completions', TRUE),
    ('hunyuan', 'hunyuan-embedding', 'Hunyuan Embedding', 'EMBEDDING', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/embeddings', TRUE),
    ('hunyuan', 'hunyuan-lite', 'Hunyuan lite', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ('hunyuan', 'hunyuan-pro', 'Hunyuan Pro', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ('hunyuan', 'hunyuan-standard', 'Hunyuan standard', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ('hunyuan', 'hunyuan-standard-256K', 'Hunyuan standard 256k', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ('jina', 'jina-embeddings-v5-omni-nano', 'Jina Embedding 5 Omni Nano', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ('jina', 'jina-embeddings-v5-omni-small', 'Jina Embedding 5 Omni Small', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ('jina', 'jina-embeddings-v5-text-nano', 'Jina Embedding 5 Text Nano', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ('jina', 'jina-embeddings-v5-text-small', 'Jina Embedding 5 Text Small', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ('jina', 'jina-reranker-v3', 'Jina Reranker 3', 'RERANK', 'jina', 'https://api.jina.ai/v1/rerank', TRUE),
    ('volcengine', 'bge-m3', 'BGE-M3', 'SPARSE_EMBEDDING', 'bge_m3', 'http://103.205.254.30:37997/encode', TRUE),
    ('volcengine', 'doubao-embedding-vision-251215', 'Doubao Vision Embedding', 'EMBEDDING', 'openai', 'https://ark.cn-beijing.volces.com/api/v3/embeddings', TRUE),
    ('volcengine', 'doubao-embedding-vision-251215', 'Doubao Vision Embedding', 'SPARSE_EMBEDDING', 'doubao_vision', 'https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal', TRUE),
    ('volcengine', 'doubao-seed-2-0-pro-260215', 'Doubao Seed 2.0 Pro', 'CHAT', 'openai', 'https://ark.cn-beijing.volces.com/api/v3/chat/completions', TRUE),
    ('minimax', 'minimax-m2.5', 'MiniMax M2.5', 'CHAT', 'openai', 'https://api.minimaxi.com/chat/completions', TRUE),
    ('mimo', 'mimo-v2.5', 'MiMo 2.5', 'CHAT', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ('mimo', 'mimo-v2.5', 'MiMo 2.5', 'VISION', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ('mimo', 'mimo-v2.5-asr', 'MiMo 2.5 ASR', 'ASR', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ('mimo', 'mimo-v2.5-pro', 'MiMo 2.5 Pro', 'CHAT', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ('huggingface', 'zai-org/GLM-5.2', 'GLM 5.2', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'deepseek-ai/DeepSeek-V4-Pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'MiniMaxAI/MiniMax-M3', 'MiniMax M3', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'MiniMaxAI/MiniMax-M3', 'MiniMax M3', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'moonshotai/Kimi-K2.7-Code', 'Kimi K2.7 Code', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('huggingface', 'moonshotai/Kimi-K2.7-Code', 'Kimi K2.7 Code', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ('openrouter', 'anthropic/claude-sonnet-5', 'Claude Sonnet 5', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('openrouter', 'anthropic/claude-sonnet-5', 'Claude Sonnet 5', 'VISION', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('openrouter', 'openai/gpt-5.5', 'GPT 5.5', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('openrouter', 'deepseek/deepseek-v4-pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('openrouter', 'z-ai/glm-5.2', 'GLM 5.2', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('openrouter', 'x-ai/grok-4.3', 'Grok 4.3', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ('siliconflow', 'BAAI/bge-reranker-v2-m3', 'BGE Reranker M3', 'RERANK', 'jina', 'https://api.siliconflow.cn/v1/rerank', TRUE),
    ('siliconflow', 'Pro/deepseek-ai/DeepSeek-V4-Flash', 'DeepSeek V4 Flash', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ('siliconflow', 'Pro/deepseek-ai/DeepSeek-V4-Pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ('siliconflow', 'Pro/moonshotai/Kimi-K2.6', 'Kimi K2.6', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ('siliconflow', 'Pro/moonshotai/Kimi-K2.6', 'Kimi K2.6', 'VISION', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ('siliconflow', 'Qwen/Qwen3-Embedding-0.6B', 'Qwen 3 Embedding 0.6b', 'EMBEDDING', 'openai', 'https://api.siliconflow.cn/v1/embeddings', TRUE);

INSERT INTO llm_provider_model (
    provider_id, model_name, display_name, capability, protocol, api_base_url, is_active
)
SELECT sp.id, seed.model_name, seed.display_name, seed.capability, seed.protocol, seed.api_base_url, seed.is_active
FROM tmp_seed_llm_provider_models seed
JOIN llm_system_provider sp ON sp.provider_type COLLATE utf8mb4_unicode_ci = seed.provider_type COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    protocol     = VALUES(protocol),
    api_base_url = VALUES(api_base_url),
    is_active    = VALUES(is_active),
    updated_at   = CURRENT_TIMESTAMP;

UPDATE llm_provider_model pm
JOIN llm_system_provider sp ON sp.id = pm.provider_id
LEFT JOIN tmp_seed_llm_provider_models seed
  ON seed.provider_type COLLATE utf8mb4_unicode_ci = sp.provider_type COLLATE utf8mb4_unicode_ci
 AND seed.model_name COLLATE utf8mb4_unicode_ci = pm.model_name COLLATE utf8mb4_unicode_ci
 AND seed.capability COLLATE utf8mb4_unicode_ci = pm.capability COLLATE utf8mb4_unicode_ci
SET pm.is_active = FALSE,
    pm.updated_at = CURRENT_TIMESTAMP
WHERE seed.provider_type IS NULL;

DELETE pm
FROM llm_provider_model pm
JOIN llm_system_provider sp ON sp.id = pm.provider_id
WHERE sp.provider_type = 'linkrag';

-- 3. LinkRag 系统兜底预设：独立维护，每个 capability 一条默认。
--    api_key 不写明文；已有预设会复用原密文，全新库需在 SOURCE 前设置 @linkrag_system_preset_api_key。
INSERT INTO llm_system_preset (
    provider_id, model_name, display_name, capability, provider_type,
    protocol, api_base_url, api_key, is_active, is_default
)
SELECT
    preset_rows.provider_id, preset_rows.model_name, preset_rows.display_name, preset_rows.capability, 'linkrag',
    preset_rows.protocol, preset_rows.api_base_url, preset_rows.api_key, TRUE, TRUE
FROM (
    SELECT
        sp.id AS provider_id,
        seed.model_name,
        seed.display_name,
        seed.capability,
        seed.protocol,
        seed.api_base_url,
        COALESCE(
            NULLIF(@linkrag_system_preset_api_key, ''),
            exact_preset.api_key,
            default_preset.api_key
        ) AS api_key
    FROM (
        SELECT 'qwen3-asr-flash' AS model_name, 'Qwen ASR Flash' AS display_name, 'ASR' AS capability, 'dashscope' AS protocol, 'https://dashscope.aliyuncs.com/api/v1' AS api_base_url
        UNION ALL SELECT 'deepseek-ai/DeepSeek-V4-Flash', 'DeepSeek V4 Flash', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions'
        UNION ALL SELECT 'BAAI/bge-m3', 'BGE-M3', 'EMBEDDING', 'openai', 'https://api.siliconflow.cn/v1/embeddings'
        UNION ALL SELECT 'BAAI/bge-reranker-v2-m3', 'BGE Reranker M3', 'RERANK', 'jina', 'https://api.siliconflow.cn/v1/rerank'
        UNION ALL SELECT 'doubao-embedding-vision-251215', 'Doubao Sparse', 'SPARSE_EMBEDDING', 'doubao_vision', 'https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal'
        UNION ALL SELECT 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'VISION', 'openai', 'https://api.siliconflow.cn/v1/chat/completions'
    ) seed
    JOIN llm_system_provider sp
      ON sp.provider_type = 'linkrag'
    LEFT JOIN llm_system_preset exact_preset
      ON exact_preset.provider_id = sp.id
     AND exact_preset.model_name = seed.model_name
     AND exact_preset.capability = seed.capability
    LEFT JOIN llm_system_preset default_preset
      ON default_preset.provider_type = 'linkrag'
     AND default_preset.capability = seed.capability
     AND default_preset.is_active = TRUE
     AND default_preset.is_default = TRUE
) preset_rows
WHERE preset_rows.api_key IS NOT NULL
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    provider_type = VALUES(provider_type),
    protocol     = VALUES(protocol),
    api_base_url = VALUES(api_base_url),
    api_key      = VALUES(api_key),
    is_active    = VALUES(is_active),
    is_default   = VALUES(is_default),
    updated_at   = CURRENT_TIMESTAMP;

UPDATE llm_system_preset preset
JOIN llm_system_provider sp
  ON sp.provider_type = 'linkrag'
JOIN (
    SELECT 'qwen3-asr-flash' AS model_name, 'ASR' AS capability
    UNION ALL SELECT 'deepseek-ai/DeepSeek-V4-Flash', 'CHAT'
    UNION ALL SELECT 'BAAI/bge-m3', 'EMBEDDING'
    UNION ALL SELECT 'BAAI/bge-reranker-v2-m3', 'RERANK'
    UNION ALL SELECT 'doubao-embedding-vision-251215', 'SPARSE_EMBEDDING'
    UNION ALL SELECT 'Qwen/Qwen3.6-27B', 'VISION'
) seed
  ON seed.capability = preset.capability
SET preset.is_default = FALSE,
    preset.updated_at = CURRENT_TIMESTAMP
WHERE preset.provider_type = 'linkrag'
  AND preset.is_default = TRUE
  AND NOT (
      preset.provider_id = sp.id
      AND preset.model_name = seed.model_name
      AND preset.capability = seed.capability
  );

UPDATE llm_system_preset preset
JOIN llm_system_provider sp
  ON sp.provider_type = 'linkrag'
LEFT JOIN (
    SELECT 'qwen3-asr-flash' AS model_name, 'ASR' AS capability
    UNION ALL SELECT 'deepseek-ai/DeepSeek-V4-Flash', 'CHAT'
    UNION ALL SELECT 'BAAI/bge-m3', 'EMBEDDING'
    UNION ALL SELECT 'BAAI/bge-reranker-v2-m3', 'RERANK'
    UNION ALL SELECT 'doubao-embedding-vision-251215', 'SPARSE_EMBEDDING'
    UNION ALL SELECT 'Qwen/Qwen3.6-27B', 'VISION'
) seed
  ON seed.model_name = preset.model_name
 AND seed.capability = preset.capability
SET preset.is_active = FALSE,
    preset.is_default = FALSE,
    preset.updated_at = CURRENT_TIMESTAMP
WHERE preset.provider_type = 'linkrag'
  AND preset.provider_id = sp.id
  AND seed.model_name IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_llm_provider_models;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_llm_providers;

COMMIT;
