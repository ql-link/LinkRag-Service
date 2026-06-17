-- =============================================================
-- 移除 OCR 模型能力
--
-- 背景：
--   OCR 与 VISION 在当前调用层重叠，Java 端不再把 OCR 作为独立 capability。
--   稀疏向量模型能力使用 SPARSE_EMBEDDING，具体模型目录由后续 seed/admin
--   按实际 Python adapter 与模型清单补充。
--
-- 执行效果：
--   1. 删除用户配置中的 OCR 行
--   2. 删除系统预设中的 OCR 行
--   3. 删除厂商模型目录中的 OCR 行
-- =============================================================

START TRANSACTION;

DELETE FROM llm_user_config
WHERE capability = 'OCR';

DELETE FROM llm_system_preset
WHERE capability = 'OCR';

DELETE FROM llm_provider_model
WHERE capability = 'OCR';

COMMIT;
