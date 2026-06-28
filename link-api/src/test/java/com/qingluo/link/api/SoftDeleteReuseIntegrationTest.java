package com.qingluo.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.service.DatasetService;
import com.qingluo.link.service.DocumentFileService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 隐性删除「同名重建 / 重传」集成测试（真实 H2，验证判别列 deleted_seq 与新唯一键）。
 *
 * <p>直接经 Mapper 造原文件行 + Service 软删，绕过异步上传，使断言确定；
 * 重点验证：软删后同名可重传/重建、删-传多轮不撞唯一约束、死行各带自身 id 互不冲突、软删文件内部打开 404。
 */
@SpringBootTest
class SoftDeleteReuseIntegrationTest {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private DocumentFileService documentFileService;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private DocumentOriginalFileMapper documentOriginalFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long USER_ID = 99977L;
    private static final Long SPARSE_CONFIG_ID = 999771L;
    private static final Long DENSE_CONFIG_ID = 999772L;
    private Long datasetId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM document_parse_pipeline");
        jdbcTemplate.update("DELETE FROM document_parsed_log");
        jdbcTemplate.update("DELETE FROM document_parse_file");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM chat_message");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset_parse_config");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM llm_user_config WHERE user_id = ?", USER_ID);
        insertEmbeddingConfig(SPARSE_CONFIG_ID, "soft-delete-sparse", "SPARSE_EMBEDDING");
        insertEmbeddingConfig(DENSE_CONFIG_ID, "soft-delete-dense", "EMBEDDING");

        Dataset ds = new Dataset();
        ds.setUserId(USER_ID);
        ds.setName("软删测试集");
        ds.setStatus("ACTIVE");
        datasetMapper.insert(ds);
        datasetId = ds.getId();
    }

    @Test
    @DisplayName("Should_AllowReuploadSameName_AfterSoftDelete")
    void Should_AllowReuploadSameName_AfterSoftDelete() {
        Long f1 = insertActiveFile("a.pdf");
        documentFileService.delete(USER_ID, f1);

        // 软删后重传同名：不应撞 uk（旧行已移到 deleted_seq=f1，活槽 deleted_seq=0 已释放）
        Long f2 = insertActiveFile("a.pdf");

        assertThat(f2).isNotEqualTo(f1);
        assertThat(activeCount("a.pdf")).isEqualTo(1);    // 列表仅见新活行
        assertThat(physicalCount("a.pdf")).isEqualTo(2);  // 旧死行物理保留（可追溯）
        assertThat(deletedSeqOf(f1)).isEqualTo(f1);       // 死行判别列=自身 id
        assertThat(deletedSeqOf(f2)).isEqualTo(0L);       // 活行判别列=0
        assertThat(objectKeyOf(f1)).isEqualTo("k/a.pdf"); // 死行保留 object_key（OSS 对象不变孤儿）
    }

    @Test
    @DisplayName("Should_AllowReuploadSameName_AcrossMultipleCycles")
    void Should_AllowReuploadSameName_AcrossMultipleCycles() {
        List<Long> deadIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Long id = insertActiveFile("a.pdf");
            // 第 2 次及以后的删除：若判别列用布尔会再撞，用自身 id 则不撞
            documentFileService.delete(USER_ID, id);
            deadIds.add(id);
        }
        Long active = insertActiveFile("a.pdf"); // 第 4 次重传仍成功

        assertThat(activeCount("a.pdf")).isEqualTo(1);
        assertThat(physicalCount("a.pdf")).isEqualTo(4);
        List<Long> seqs = jdbcTemplate.queryForList(
            "SELECT deleted_seq FROM document_original_file "
                + "WHERE dataset_id = ? AND original_filename = ? AND is_deleted = true",
            Long.class, datasetId, "a.pdf");
        assertThat(seqs).containsExactlyInAnyOrderElementsOf(deadIds); // 每条死行判别列=各自 id、互不相同
        assertThat(deletedSeqOf(active)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should_AllowRecreateSameDatasetName_AfterSoftDelete")
    void Should_AllowRecreateSameDatasetName_AfterSoftDelete() {
        CreateDatasetRequest req = new CreateDatasetRequest();
        req.setName("财务");
        req.setSparseEmbeddingConfigId(SPARSE_CONFIG_ID);
        req.setDenseEmbeddingConfigId(DENSE_CONFIG_ID);
        DatasetDTO d1 = datasetService.create(USER_ID, req);
        datasetService.delete(USER_ID, d1.getId());

        // 软删数据集后重建同名：不应报"已存在同名数据集"
        DatasetDTO d2 = datasetService.create(USER_ID, req);

        assertThat(d2.getId()).isNotEqualTo(d1.getId());
        Integer physical = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dataset WHERE user_id = ? AND name = ?", Integer.class, USER_ID, "财务");
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dataset WHERE user_id = ? AND name = ? AND is_deleted = false",
            Integer.class, USER_ID, "财务");
        assertThat(physical).isEqualTo(2); // 旧死行 + 新活行
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("Should_Return404_When_OpenSoftDeletedOriginalFile")
    void Should_Return404_When_OpenSoftDeletedOriginalFile() {
        Long f = insertActiveFile("b.pdf");
        documentFileService.delete(USER_ID, f);

        // 软删文件经 @TableLogic 过滤，内部原文件打开返回 404
        assertThatThrownBy(() -> documentFileService.openOriginalFile(f))
            .isInstanceOf(BusinessException.class)
            .hasMessage("文件不存在");
    }

    // ---- helpers ----

    private Long insertActiveFile(String filename) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setDatasetId(datasetId);
        file.setUserId(USER_ID);
        file.setOriginalFilename(filename);
        file.setFileSuffix("pdf");
        file.setFileSize(1L);
        file.setBucketName("local-private");
        file.setObjectKey("k/" + filename);
        file.setUploadStatus("success");
        file.setIsUploadSuccess(true);
        documentOriginalFileMapper.insert(file);
        return file.getId();
    }

    private void insertEmbeddingConfig(Long id, String modelName, String capability) {
        jdbcTemplate.update("""
            INSERT INTO llm_user_config (
                id, user_id, provider_id, provider_type, api_key, api_base_url, protocol,
                model_name, capability, is_active, is_default, is_system_preset
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, false, false)
            """, id, USER_ID, 1L, "aliyun", "encrypted-key",
            "https://example.com/embeddings", "openai", modelName, capability);
    }

    private Integer activeCount(String filename) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file "
                + "WHERE dataset_id = ? AND original_filename = ? AND is_deleted = false",
            Integer.class, datasetId, filename);
    }

    private Integer physicalCount(String filename) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE dataset_id = ? AND original_filename = ?",
            Integer.class, datasetId, filename);
    }

    private Long deletedSeqOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT deleted_seq FROM document_original_file WHERE id = ?", Long.class, id);
    }

    private String objectKeyOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, id);
    }
}
