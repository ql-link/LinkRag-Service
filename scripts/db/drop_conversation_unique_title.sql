-- 允许同一用户同一数据集下存在重名对话。
-- 存量库如曾执行 add_conversation_unique_title.sql，需要先删除该唯一键。
ALTER TABLE chat_conversation
  DROP INDEX `uk_conversation_user_dataset_title`;
