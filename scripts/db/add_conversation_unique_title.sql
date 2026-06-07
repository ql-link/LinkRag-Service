-- 存量去重：同一用户同一数据集下保留最新（id 最大）的对话，删除其余同名行
DELETE c1 FROM chat_conversation c1
INNER JOIN chat_conversation c2
  ON  c1.user_id    = c2.user_id
  AND c1.dataset_id = c2.dataset_id
  AND c1.title      = c2.title
  AND c1.id         < c2.id;

-- 新增唯一约束：同一用户在同一数据集下对话标题不可重复
ALTER TABLE chat_conversation
  ADD UNIQUE KEY `uk_conversation_user_dataset_title` (`user_id`, `dataset_id`, `title`);
