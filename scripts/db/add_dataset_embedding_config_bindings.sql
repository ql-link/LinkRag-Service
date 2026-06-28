-- 为数据集解析/检索配置增加稀疏/稠密向量模型绑定。
-- 说明：
-- 1. 新建数据集由 Java API 强制写入两个配置 ID。
-- 2. 历史数据集需要按用户实际可用的 EMBEDDING / SPARSE_EMBEDDING 配置回填；
--    未回填的数据集在召回 session 签发时会被拒绝，避免继续使用漂移的默认模型。

ALTER TABLE dataset_parse_config
    ADD COLUMN sparse_embedding_config_id BIGINT UNSIGNED DEFAULT NULL COMMENT '稀疏向量模型配置 ID，对应 llm_user_config.id，能力为 SPARSE_EMBEDDING' AFTER dataset_id,
    ADD COLUMN dense_embedding_config_id BIGINT UNSIGNED DEFAULT NULL COMMENT '稠密向量模型配置 ID，对应 llm_user_config.id，能力为 EMBEDDING' AFTER sparse_embedding_config_id,
    ADD INDEX idx_dataset_parse_sparse_config (sparse_embedding_config_id),
    ADD INDEX idx_dataset_parse_dense_config (dense_embedding_config_id);
