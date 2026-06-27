-- LinkRag 系统兜底配置迁移：
-- 1. 引入 LinkRag 系统服务厂商；
-- 2. 将 llm_system_preset 从“注册镜像模板”升级为“系统兜底配置”，增加 is_default；
-- 3. 为按能力解析系统默认预设补索引。

INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, default_protocol, is_active, priority)
SELECT 'linkrag', 'LinkRag', 'https://api.linkrag.local/v1', 'openai', TRUE, 110
WHERE NOT EXISTS (
    SELECT 1 FROM llm_system_provider WHERE provider_type = 'linkrag'
);

ALTER TABLE llm_system_preset
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为该能力的系统兜底默认配置';

CREATE INDEX idx_system_preset_default
    ON llm_system_preset (provider_type, capability, is_active, is_default);

-- 运维需在迁移后为 LinkRag 补齐 llm_provider_model 与 llm_system_preset：
-- 每个需要兜底的 capability 只能保留一条 active + is_default=true 的系统预设。
