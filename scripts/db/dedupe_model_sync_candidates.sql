-- 清理外部模型同步候选重复行，并为后续重复刷新补唯一键保护。
-- 保留同一 (provider_id, sync_source, model_name, inferred_capability) 下 id 最大的一条候选。

USE tolink_rag_db;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_sync_candidate_keep AS
SELECT MAX(id) AS keep_id
FROM llm_provider_model_sync_candidate
GROUP BY provider_id, sync_source, model_name, inferred_capability;

DELETE c
FROM llm_provider_model_sync_candidate c
LEFT JOIN tmp_sync_candidate_keep k ON k.keep_id = c.id
WHERE k.keep_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_sync_candidate_keep;

COMMIT;

SET @idx_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'llm_provider_model_sync_candidate'
      AND index_name = 'uk_sync_candidate_provider_source_model_cap'
);

SET @ddl = IF(
    @idx_exists = 0,
    'ALTER TABLE llm_provider_model_sync_candidate ADD UNIQUE KEY uk_sync_candidate_provider_source_model_cap (provider_id, sync_source, model_name, inferred_capability)',
    'SELECT ''uk_sync_candidate_provider_source_model_cap already exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
