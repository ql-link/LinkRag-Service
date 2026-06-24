-- llm_usage_log 瘦身（LINK-191）：删 4 列 + 2 索引，去掉对话级关联键。
-- 对应 Python 迁移 ql-link/LinkRag migrations/versions/0025_20260623_usage_log_slim.py。
-- 自此 generate 用量统一经 usage_report 通道落库（stage=chat/operation=generate），
-- 不再保留 conversation_id/message_id/request_id，无法回溯到具体对话（有意为之）。
-- 权威口径见 docs/api/mysql_schema.md、docs/api/mq_contracts.md。
--
-- 部署时序（硬依赖）：必须先发布 Java（chat_turn 停写 llm_usage_log、usage_report 停写 4 个被删列、
-- 兼容 operation=generate）并灰度，再执行本迁移；否则旧代码 INSERT 已删列会 Unknown column 失败、用量丢失。
-- 注意：MySQL 不支持 IF EXISTS 子句于 DROP COLUMN，存量库执行前请确认列/索引状态。

-- 1. 先删两个对话级关联索引（依赖被删列）。
ALTER TABLE llm_usage_log DROP INDEX idx_conversation_id;
ALTER TABLE llm_usage_log DROP INDEX idx_usage_message_id;

-- 2. 删 4 列：fallback_config_id（死字段）、conversation_id / message_id / request_id（对话级关联键）。
ALTER TABLE llm_usage_log
    DROP COLUMN fallback_config_id,
    DROP COLUMN conversation_id,
    DROP COLUMN message_id,
    DROP COLUMN request_id;
