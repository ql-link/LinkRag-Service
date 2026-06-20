-- 全链路用量上报（LINK-184）：llm_usage_log 升级为「全链路模型调用账本」。
-- 增补 stage / operation 两列，并放开 config_id 的 NOT NULL（系统配置调用如召回 query 编码无 config_id）。
-- 权威口径见 docs/api/mq_contracts.md（§用量上报）与 ql-link/LinkRag usage_report 消息。
-- 注意：MySQL 不支持 IF [NOT] EXISTS 子句于 ADD/MODIFY COLUMN，存量库执行前请确认列状态。

-- 1. 先以可空方式补列，便于回填存量行。
ALTER TABLE llm_usage_log
    ADD COLUMN stage     VARCHAR(16) COMMENT '调用阶段：parse/recall/chat' AFTER model_name,
    ADD COLUMN operation VARCHAR(16) COMMENT '调用操作：embed/rerank/vision/table/generate' AFTER stage;

-- 2. 回填存量行：本次升级前所有用量均来自对话最终生成（chat_turn 通道）。
UPDATE llm_usage_log SET stage = 'chat', operation = 'generate' WHERE stage IS NULL;

-- 3. 收紧为 NOT NULL，与 init.sql 全新建表口径一致。
ALTER TABLE llm_usage_log MODIFY COLUMN stage     VARCHAR(16) NOT NULL COMMENT '调用阶段：parse/recall/chat';
ALTER TABLE llm_usage_log MODIFY COLUMN operation VARCHAR(16) NOT NULL COMMENT '调用操作：embed/rerank/vision/table/generate';

-- 4. 放开 config_id：系统配置调用（召回 query 编码等）无 config_id，落 NULL。
ALTER TABLE llm_usage_log MODIFY COLUMN config_id BIGINT UNSIGNED COMMENT '用户配置 ID；系统配置调用为 NULL';

-- 5. 按 (stage, operation) 聚账查询索引。
ALTER TABLE llm_usage_log ADD INDEX idx_usage_stage_operation (stage, operation);
