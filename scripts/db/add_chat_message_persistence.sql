-- 对话轮次落库（LINK-180 / 对应 LinkRag 迁移 0021）
-- chat_message 收缩为「一行一轮」；llm_usage_log 增补 message_id / request_id。
-- 权威迁移在 Python 仓库（ql-link/LinkRag migrations 0021），本脚本供 Java 侧手工对齐 / 本地初始化使用。
-- 注意：MySQL 不支持 IF [NOT] EXISTS 子句于 ADD/DROP COLUMN，存量库执行前请确认列状态。

-- ===== chat_message：一行一轮 =====
ALTER TABLE chat_message
    ADD COLUMN `query`      MEDIUMTEXT  COMMENT '用户提问' AFTER model_name,
    ADD COLUMN request_id   VARCHAR(64) COMMENT '请求追踪 ID / 幂等键' AFTER `query`,
    ADD COLUMN `references`  JSON        COMMENT '召回片段 chunk_id 列表（仅标识，不含正文）' AFTER request_id,
    ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'success' COMMENT '轮次状态：success/partial/failed' AFTER `references`;

-- content 改名为 answer（保留历史数据）
ALTER TABLE chat_message CHANGE COLUMN content answer MEDIUMTEXT COMMENT 'LLM 回答（partial 为半截，failed 可空）';

-- 删除一行一轮模型不再需要的列
ALTER TABLE chat_message DROP COLUMN role;
ALTER TABLE chat_message DROP COLUMN token_count;

-- 幂等去重查询索引（去重逻辑在 Java 消费侧以 request_id 做存在性校验）
ALTER TABLE chat_message ADD INDEX idx_chat_message_request_id (request_id);

-- ===== llm_usage_log：关联 message / request =====
ALTER TABLE llm_usage_log
    ADD COLUMN message_id BIGINT UNSIGNED COMMENT '关联消息 ID（chat_message.id）' AFTER conversation_id,
    ADD COLUMN request_id VARCHAR(64)     COMMENT '请求追踪 ID / 幂等键（与 chat_message.request_id 一致）' AFTER message_id;

ALTER TABLE llm_usage_log ADD INDEX idx_usage_message_id (message_id);
