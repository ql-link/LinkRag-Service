-- =============================================================
-- toLink-Service：LLM 厂商与模型目录种子数据（精简主力厂商版）
-- 生成日期：2026-07-02
-- 来源：本地 Docker MySQL tolink_rag_db.llm_system_provider / llm_provider_model / llm_system_preset
-- 厂商：17 个；厂商模型能力记录：83 条；默认上架模型能力：83 条；LinkRag 系统预设：6 条。
-- 策略：只保留国内/国外主力厂商与 LinkRag 系统厂商；每个厂商最多保留 5 个当前主推模型。
-- LinkRag 只注册系统厂商，不写入 llm_provider_model；其模型只写入 llm_system_preset。
-- 全新环境首次写入系统预设前需先设置加密平台 Key：
--   SET @linkrag_system_preset_api_key = '<AES-256-GCM 加密后的平台 Key 密文>';
-- display_name 保留主版本号（如 2.5 / 4.8），不保留发布日期或快照号。
-- =============================================================

USE tolink_rag_db;

START TRANSACTION;

-- 1. 厂商基本信息：直接写入主力厂商白名单，并下架历史非白名单厂商。
INSERT INTO llm_system_provider (
    provider_type, provider_name, icon_url, icon_object_key, api_base_url, default_protocol, is_active, priority
)
VALUES
    ('linkrag', 'LinkRag', 'http://localhost:39000/tolink-public/providerIcon/linkrag.png', 'providerIcon/linkrag.png', 'https://api.siliconflow.cn/v1', 'openai', TRUE, 100),
    ('aliyun', 'Aliyun', 'http://localhost:39000/tolink-public/providerIcon/aliyun.svg', 'providerIcon/aliyun.svg', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'openai', TRUE, 99),
    ('claude', 'Anthropic', 'http://localhost:39000/tolink-public/providerIcon/claude.svg', 'providerIcon/claude.svg', 'https://api.anthropic.com', 'anthropic', TRUE, 99),
    ('deepseek', 'DeepSeek', 'http://localhost:39000/tolink-public/providerIcon/deepseek.svg', 'providerIcon/deepseek.svg', 'https://api.deepseek.com/v1', 'openai', TRUE, 99),
    ('gemini', 'Google', 'http://localhost:39000/tolink-public/providerIcon/gemini.svg', 'providerIcon/gemini.svg', 'https://generativelanguage.googleapis.com/v1beta', 'google', TRUE, 99),
    ('glm', 'ZHIPU-AI', 'http://localhost:39000/tolink-public/providerIcon/glm.svg', 'providerIcon/glm.svg', 'https://open.bigmodel.cn/api/paas/v4', 'openai', TRUE, 99),
    ('moonshot', 'Moonshot', 'http://localhost:39000/tolink-public/providerIcon/moonshot.svg', 'providerIcon/moonshot.svg', 'https://api.moonshot.cn/v1', 'openai', TRUE, 99),
    ('openai', 'OpenAI', 'http://localhost:39000/tolink-public/providerIcon/openai.svg', 'providerIcon/openai.svg', 'https://api.openai.com/v1', 'openai', TRUE, 99),
    ('xai', 'xAI', 'http://localhost:39000/tolink-public/providerIcon/xai.svg', 'providerIcon/xai.svg', 'https://api.x.ai/v1', 'openai', TRUE, 99),
    ('huggingface', 'HuggingFace', 'http://localhost:39000/tolink-public/providerIcon/huggingface.svg', 'providerIcon/huggingface.svg', 'https://router.huggingface.co/v1', 'openai', TRUE, 98),
    ('minimax', 'MiniMax', 'http://localhost:39000/tolink-public/providerIcon/minimax.svg', 'providerIcon/minimax.svg', 'https://api.minimaxi.com/', 'openai', TRUE, 98),
    ('openrouter', 'OpenRouter', 'http://localhost:39000/tolink-public/providerIcon/openrouter.svg', 'providerIcon/openrouter.svg', 'https://openrouter.ai/api/v1', 'openai', TRUE, 98),
    ('hunyuan', 'HunYuan', 'http://localhost:39000/tolink-public/providerIcon/hunyuan.svg', 'providerIcon/hunyuan.svg', 'https://api.hunyuan.cloud.tencent.com/v1', 'openai', TRUE, 50),
    ('jina', 'Jina', 'http://localhost:39000/tolink-public/providerIcon/jina.svg', 'providerIcon/jina.svg', 'https://api.jina.ai/v1', 'jina', TRUE, 50),
    ('mimo', 'Xiaomi MiMo Token Plan', 'http://localhost:39000/tolink-public/providerIcon/mimo.svg', 'providerIcon/mimo.svg', 'https://token-plan-cn.xiaomimimo.com/v1', 'openai', TRUE, 50),
    ('siliconflow', 'SiliconFlow', 'http://localhost:39000/tolink-public/providerIcon/siliconflow.svg', 'providerIcon/siliconflow.svg', 'https://api.siliconflow.cn/v1', 'openai', TRUE, 50),
    ('volcengine', 'VolcEngine', 'http://localhost:39000/tolink-public/providerIcon/volcengine.svg', 'providerIcon/volcengine.svg', 'https://ark.cn-beijing.volces.com/api/v3', 'openai', TRUE, 50)
ON DUPLICATE KEY UPDATE
    provider_name    = VALUES(provider_name),
    icon_url         = VALUES(icon_url),
    icon_object_key  = VALUES(icon_object_key),
    api_base_url     = VALUES(api_base_url),
    default_protocol = VALUES(default_protocol),
    is_active        = VALUES(is_active),
    priority         = VALUES(priority),
    updated_at       = CURRENT_TIMESTAMP;

UPDATE llm_system_provider
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_type NOT IN (
    'linkrag', 'aliyun', 'claude', 'deepseek', 'gemini', 'glm',
    'moonshot', 'openai', 'xai', 'huggingface', 'minimax', 'openrouter',
    'hunyuan', 'jina', 'mimo', 'siliconflow', 'volcengine'
);

-- 2. 模型能力目录：直接写入当前主推模型；未列入当前种子的历史模型能力会被下架。
INSERT INTO llm_provider_model (
    provider_id, model_name, display_name, capability, protocol, api_base_url, is_active
)
VALUES
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3-asr-flash', 'Qwen 3 ASR Flash', 'ASR', 'dashscope', 'https://dashscope.aliyuncs.com/api/v1', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3-max', 'Qwen 3 Max', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3-rerank', 'Qwen 3 Rerank', 'RERANK', 'dashscope', 'https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3.5-flash', 'Qwen 3.5 Flash', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3.5-flash', 'Qwen 3.5 Flash', 'VISION', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3.5-plus', 'Qwen 3.5 Plus', 'CHAT', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'aliyun'), 'qwen3.5-plus', 'Qwen 3.5 Plus', 'VISION', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-haiku-4-5-20251001', 'Claude Haiku 4.5', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-haiku-4-5-20251001', 'Claude Haiku 4.5', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-opus-4-7', 'Claude Opus 4.7', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-opus-4-7', 'Claude Opus 4.7', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-opus-4-8', 'Claude Opus 4.8', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-opus-4-8', 'Claude Opus 4.8', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-sonnet-4-5-20250929', 'Claude Sonnet 4.5', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-sonnet-4-5-20250929', 'Claude Sonnet 4.5', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-sonnet-4-6', 'Claude Sonnet 4.6', 'CHAT', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'claude'), 'claude-sonnet-4-6', 'Claude Sonnet 4.6', 'VISION', 'anthropic', 'https://api.anthropic.com/v1/messages', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'deepseek'), 'deepseek-v4-flash', 'DeepSeek V4 Flash', 'CHAT', 'openai', 'https://api.deepseek.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'deepseek'), 'deepseek-v4-pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://api.deepseek.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'gemini'), 'gemini-2.5-flash', 'Gemini 2.5 Flash', 'CHAT', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'gemini'), 'gemini-2.5-flash', 'Gemini 2.5 Flash', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'gemini'), 'gemini-2.5-pro', 'Gemini 2.5 Pro', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'gemini'), 'gemini-3-pro-preview', 'Gemini 3 Pro', 'VISION', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'gemini'), 'gemini-embedding-001', 'Gemini Embedding', 'EMBEDDING', 'google', 'https://generativelanguage.googleapis.com/v1beta', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'glm'), 'embedding-3', 'GLM Embedding 3', 'EMBEDDING', 'openai', 'https://open.bigmodel.cn/api/paas/v4/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'glm'), 'glm-5', 'GLM 5', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'glm'), 'glm-5-turbo', 'GLM 5 Turbo', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'glm'), 'glm-5v-turbo', 'GLM 5V Turbo', 'CHAT', 'openai', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'glm'), 'glm-asr-2512', 'GLM ASR', 'ASR', 'openai', 'https://open.bigmodel.cn/api/paas/v4/audio/transcriptions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'moonshot'), 'kimi-k2-thinking', 'Kimi K2 Thinking', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'moonshot'), 'kimi-k2-thinking-turbo', 'Kimi K2 Thinking Turbo', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'moonshot'), 'kimi-k2.6', 'Kimi K2.6', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'moonshot'), 'kimi-k2.6', 'Kimi K2.6', 'VISION', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'moonshot'), 'kimi-latest', 'Kimi', 'CHAT', 'openai', 'https://api.moonshot.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5-mini', 'GPT 5 Mini', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5-mini', 'GPT 5 Mini', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.1-chat-latest', 'GPT 5.1 Chat', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.1-chat-latest', 'GPT 5.1 Chat', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.4', 'GPT 5.4', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.4', 'GPT 5.4', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.5', 'GPT 5.5', 'CHAT', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'gpt-5.5', 'GPT 5.5', 'VISION', 'openai', 'https://api.openai.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openai'), 'text-embedding-3-large', 'OpenAI Embedding 3 Large', 'EMBEDDING', 'openai', 'https://api.openai.com/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'xai'), 'grok-3-fast', 'Grok 3 Fast', 'CHAT', 'openai', 'https://api.x.ai/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'xai'), 'grok-4', 'Grok 4', 'CHAT', 'openai', 'https://api.x.ai/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'deepseek-ai/DeepSeek-V4-Pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'MiniMaxAI/MiniMax-M3', 'MiniMax M3', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'MiniMaxAI/MiniMax-M3', 'MiniMax M3', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'moonshotai/Kimi-K2.7-Code', 'Kimi K2.7 Code', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'moonshotai/Kimi-K2.7-Code', 'Kimi K2.7 Code', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'VISION', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'huggingface'), 'zai-org/GLM-5.2', 'GLM 5.2', 'CHAT', 'openai', 'https://router.huggingface.co/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'minimax'), 'minimax-m2.5', 'MiniMax M2.5', 'CHAT', 'openai', 'https://api.minimaxi.com/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'anthropic/claude-sonnet-5', 'Claude Sonnet 5', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'anthropic/claude-sonnet-5', 'Claude Sonnet 5', 'VISION', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'deepseek/deepseek-v4-pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'openai/gpt-5.5', 'GPT 5.5', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'x-ai/grok-4.3', 'Grok 4.3', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'openrouter'), 'z-ai/glm-5.2', 'GLM 5.2', 'CHAT', 'openai', 'https://openrouter.ai/api/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'hunyuan'), 'hunyuan-embedding', 'Hunyuan Embedding', 'EMBEDDING', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'hunyuan'), 'hunyuan-lite', 'Hunyuan lite', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'hunyuan'), 'hunyuan-pro', 'Hunyuan Pro', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'hunyuan'), 'hunyuan-standard', 'Hunyuan standard', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'hunyuan'), 'hunyuan-standard-256K', 'Hunyuan standard 256k', 'CHAT', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'jina'), 'jina-embeddings-v5-omni-nano', 'Jina Embedding 5 Omni Nano', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'jina'), 'jina-embeddings-v5-omni-small', 'Jina Embedding 5 Omni Small', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'jina'), 'jina-embeddings-v5-text-nano', 'Jina Embedding 5 Text Nano', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'jina'), 'jina-embeddings-v5-text-small', 'Jina Embedding 5 Text Small', 'EMBEDDING', 'jina', 'https://api.jina.ai/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'jina'), 'jina-reranker-v3', 'Jina Reranker 3', 'RERANK', 'jina', 'https://api.jina.ai/v1/rerank', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'mimo'), 'mimo-v2.5', 'MiMo 2.5', 'CHAT', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'mimo'), 'mimo-v2.5', 'MiMo 2.5', 'VISION', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'mimo'), 'mimo-v2.5-asr', 'MiMo 2.5 ASR', 'ASR', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'mimo'), 'mimo-v2.5-pro', 'MiMo 2.5 Pro', 'CHAT', 'openai', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'BAAI/bge-reranker-v2-m3', 'BGE Reranker M3', 'RERANK', 'jina', 'https://api.siliconflow.cn/v1/rerank', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'Pro/deepseek-ai/DeepSeek-V4-Flash', 'DeepSeek V4 Flash', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'Pro/deepseek-ai/DeepSeek-V4-Pro', 'DeepSeek V4 Pro', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'Pro/moonshotai/Kimi-K2.6', 'Kimi K2.6', 'CHAT', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'Pro/moonshotai/Kimi-K2.6', 'Kimi K2.6', 'VISION', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'siliconflow'), 'Qwen/Qwen3-Embedding-0.6B', 'Qwen 3 Embedding 0.6b', 'EMBEDDING', 'openai', 'https://api.siliconflow.cn/v1/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'volcengine'), 'doubao-embedding-vision-251215', 'Doubao Vision Embedding', 'EMBEDDING', 'openai', 'https://ark.cn-beijing.volces.com/api/v3/embeddings', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'volcengine'), 'doubao-embedding-vision-251215', 'Doubao Vision Embedding', 'SPARSE_EMBEDDING', 'doubao_vision', 'https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal', TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'volcengine'), 'doubao-seed-2-0-pro-260215', 'Doubao Seed 2.0 Pro', 'CHAT', 'openai', 'https://ark.cn-beijing.volces.com/api/v3/chat/completions', TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    protocol     = VALUES(protocol),
    api_base_url = VALUES(api_base_url),
    is_active    = VALUES(is_active),
    updated_at   = CURRENT_TIMESTAMP;

UPDATE llm_provider_model pm
JOIN llm_system_provider sp ON sp.id = pm.provider_id
SET pm.is_active = FALSE,
    pm.updated_at = CURRENT_TIMESTAMP
WHERE (sp.provider_type, pm.model_name, pm.capability) NOT IN (
    ('aliyun', 'qwen3-asr-flash', 'ASR'),
    ('aliyun', 'qwen3-max', 'CHAT'),
    ('aliyun', 'qwen3-rerank', 'RERANK'),
    ('aliyun', 'qwen3.5-flash', 'CHAT'),
    ('aliyun', 'qwen3.5-flash', 'VISION'),
    ('aliyun', 'qwen3.5-plus', 'CHAT'),
    ('aliyun', 'qwen3.5-plus', 'VISION'),
    ('claude', 'claude-haiku-4-5-20251001', 'CHAT'),
    ('claude', 'claude-haiku-4-5-20251001', 'VISION'),
    ('claude', 'claude-opus-4-7', 'CHAT'),
    ('claude', 'claude-opus-4-7', 'VISION'),
    ('claude', 'claude-opus-4-8', 'CHAT'),
    ('claude', 'claude-opus-4-8', 'VISION'),
    ('claude', 'claude-sonnet-4-5-20250929', 'CHAT'),
    ('claude', 'claude-sonnet-4-5-20250929', 'VISION'),
    ('claude', 'claude-sonnet-4-6', 'CHAT'),
    ('claude', 'claude-sonnet-4-6', 'VISION'),
    ('deepseek', 'deepseek-v4-flash', 'CHAT'),
    ('deepseek', 'deepseek-v4-pro', 'CHAT'),
    ('gemini', 'gemini-2.5-flash', 'CHAT'),
    ('gemini', 'gemini-2.5-flash', 'VISION'),
    ('gemini', 'gemini-2.5-pro', 'VISION'),
    ('gemini', 'gemini-3-pro-preview', 'VISION'),
    ('gemini', 'gemini-embedding-001', 'EMBEDDING'),
    ('glm', 'embedding-3', 'EMBEDDING'),
    ('glm', 'glm-5', 'CHAT'),
    ('glm', 'glm-5-turbo', 'CHAT'),
    ('glm', 'glm-5v-turbo', 'CHAT'),
    ('glm', 'glm-asr-2512', 'ASR'),
    ('moonshot', 'kimi-k2-thinking', 'CHAT'),
    ('moonshot', 'kimi-k2-thinking-turbo', 'CHAT'),
    ('moonshot', 'kimi-k2.6', 'CHAT'),
    ('moonshot', 'kimi-k2.6', 'VISION'),
    ('moonshot', 'kimi-latest', 'CHAT'),
    ('openai', 'gpt-5-mini', 'CHAT'),
    ('openai', 'gpt-5-mini', 'VISION'),
    ('openai', 'gpt-5.1-chat-latest', 'CHAT'),
    ('openai', 'gpt-5.1-chat-latest', 'VISION'),
    ('openai', 'gpt-5.4', 'CHAT'),
    ('openai', 'gpt-5.4', 'VISION'),
    ('openai', 'gpt-5.5', 'CHAT'),
    ('openai', 'gpt-5.5', 'VISION'),
    ('openai', 'text-embedding-3-large', 'EMBEDDING'),
    ('xai', 'grok-3-fast', 'CHAT'),
    ('xai', 'grok-4', 'CHAT'),
    ('huggingface', 'deepseek-ai/DeepSeek-V4-Pro', 'CHAT'),
    ('huggingface', 'MiniMaxAI/MiniMax-M3', 'CHAT'),
    ('huggingface', 'MiniMaxAI/MiniMax-M3', 'VISION'),
    ('huggingface', 'moonshotai/Kimi-K2.7-Code', 'CHAT'),
    ('huggingface', 'moonshotai/Kimi-K2.7-Code', 'VISION'),
    ('huggingface', 'Qwen/Qwen3.6-27B', 'CHAT'),
    ('huggingface', 'Qwen/Qwen3.6-27B', 'VISION'),
    ('huggingface', 'zai-org/GLM-5.2', 'CHAT'),
    ('minimax', 'minimax-m2.5', 'CHAT'),
    ('openrouter', 'anthropic/claude-sonnet-5', 'CHAT'),
    ('openrouter', 'anthropic/claude-sonnet-5', 'VISION'),
    ('openrouter', 'deepseek/deepseek-v4-pro', 'CHAT'),
    ('openrouter', 'openai/gpt-5.5', 'CHAT'),
    ('openrouter', 'x-ai/grok-4.3', 'CHAT'),
    ('openrouter', 'z-ai/glm-5.2', 'CHAT'),
    ('hunyuan', 'hunyuan-embedding', 'EMBEDDING'),
    ('hunyuan', 'hunyuan-lite', 'CHAT'),
    ('hunyuan', 'hunyuan-pro', 'CHAT'),
    ('hunyuan', 'hunyuan-standard', 'CHAT'),
    ('hunyuan', 'hunyuan-standard-256K', 'CHAT'),
    ('jina', 'jina-embeddings-v5-omni-nano', 'EMBEDDING'),
    ('jina', 'jina-embeddings-v5-omni-small', 'EMBEDDING'),
    ('jina', 'jina-embeddings-v5-text-nano', 'EMBEDDING'),
    ('jina', 'jina-embeddings-v5-text-small', 'EMBEDDING'),
    ('jina', 'jina-reranker-v3', 'RERANK'),
    ('mimo', 'mimo-v2.5', 'CHAT'),
    ('mimo', 'mimo-v2.5', 'VISION'),
    ('mimo', 'mimo-v2.5-asr', 'ASR'),
    ('mimo', 'mimo-v2.5-pro', 'CHAT'),
    ('siliconflow', 'BAAI/bge-reranker-v2-m3', 'RERANK'),
    ('siliconflow', 'Pro/deepseek-ai/DeepSeek-V4-Flash', 'CHAT'),
    ('siliconflow', 'Pro/deepseek-ai/DeepSeek-V4-Pro', 'CHAT'),
    ('siliconflow', 'Pro/moonshotai/Kimi-K2.6', 'CHAT'),
    ('siliconflow', 'Pro/moonshotai/Kimi-K2.6', 'VISION'),
    ('siliconflow', 'Qwen/Qwen3-Embedding-0.6B', 'EMBEDDING'),
    ('volcengine', 'doubao-embedding-vision-251215', 'EMBEDDING'),
    ('volcengine', 'doubao-embedding-vision-251215', 'SPARSE_EMBEDDING'),
    ('volcengine', 'doubao-seed-2-0-pro-260215', 'CHAT')
);

DELETE pm
FROM llm_provider_model pm
JOIN llm_system_provider sp ON sp.id = pm.provider_id
WHERE sp.provider_type = 'linkrag';

-- 3. LinkRag 系统兜底预设：直接写入原表，每个 capability 一条默认。
--    api_key 不写明文；已有预设会复用原密文，全新库需在 SOURCE 前设置 @linkrag_system_preset_api_key。
INSERT INTO llm_system_preset (
    provider_id, model_name, display_name, capability, provider_type,
    protocol, api_base_url, api_key, is_active, is_default
)
VALUES
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'qwen3-asr-flash', 'Qwen ASR Flash', 'ASR', 'linkrag', 'dashscope', 'https://dashscope.aliyuncs.com/api/v1', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'deepseek-ai/DeepSeek-V4-Flash', 'DeepSeek V4 Flash', 'CHAT', 'linkrag', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'BAAI/bge-m3', 'BGE-M3', 'EMBEDDING', 'linkrag', 'openai', 'https://api.siliconflow.cn/v1/embeddings', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'BAAI/bge-reranker-v2-m3', 'BGE Reranker M3', 'RERANK', 'linkrag', 'jina', 'https://api.siliconflow.cn/v1/rerank', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'doubao-embedding-vision-251215', 'Doubao Sparse', 'SPARSE_EMBEDDING', 'linkrag', 'doubao_vision', 'https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE),
    ((SELECT id FROM llm_system_provider WHERE provider_type = 'linkrag'), 'Qwen/Qwen3.6-27B', 'Qwen 3.6 27B', 'VISION', 'linkrag', 'openai', 'https://api.siliconflow.cn/v1/chat/completions', COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), ''), TRUE, TRUE)
ON DUPLICATE KEY UPDATE
    display_name  = VALUES(display_name),
    provider_type = VALUES(provider_type),
    protocol      = VALUES(protocol),
    api_base_url  = VALUES(api_base_url),
    api_key       = COALESCE(NULLIF(@linkrag_system_preset_api_key, ''), api_key),
    is_active     = VALUES(is_active),
    is_default    = VALUES(is_default),
    updated_at    = CURRENT_TIMESTAMP;

UPDATE llm_system_preset
SET is_default = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_type = 'linkrag'
  AND is_default = TRUE
  AND (model_name, capability) NOT IN (
      ('qwen3-asr-flash', 'ASR'),
      ('deepseek-ai/DeepSeek-V4-Flash', 'CHAT'),
      ('BAAI/bge-m3', 'EMBEDDING'),
      ('BAAI/bge-reranker-v2-m3', 'RERANK'),
      ('doubao-embedding-vision-251215', 'SPARSE_EMBEDDING'),
      ('Qwen/Qwen3.6-27B', 'VISION')
  );

UPDATE llm_system_preset
SET is_active = FALSE,
    is_default = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_type = 'linkrag'
  AND (model_name, capability) NOT IN (
      ('qwen3-asr-flash', 'ASR'),
      ('deepseek-ai/DeepSeek-V4-Flash', 'CHAT'),
      ('BAAI/bge-m3', 'EMBEDDING'),
      ('BAAI/bge-reranker-v2-m3', 'RERANK'),
      ('doubao-embedding-vision-251215', 'SPARSE_EMBEDDING'),
      ('Qwen/Qwen3.6-27B', 'VISION')
  );

COMMIT;
