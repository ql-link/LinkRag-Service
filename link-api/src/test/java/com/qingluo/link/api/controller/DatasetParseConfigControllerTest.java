package com.qingluo.link.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 数据集解析/检索配置接口端到端测试。覆盖 acceptance 18 Scenario：GET 回显 / PUT 全量覆盖 / 校验 / 权限 / 幂等，
 * 并验证 JSON 列经 JacksonTypeHandler 的存取往返（snake_case、召回新增项补默认、忽略历史模型字段）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatasetParseConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long ALICE_ID = 99988L;
    private static final Long BOB_ID = 99987L;
    private static final Long ALICE_SPARSE_CONFIG_ID = 99781L;
    private static final Long ALICE_DENSE_CONFIG_ID = 99782L;
    private static final Long BOB_SPARSE_CONFIG_ID = 99783L;
    private static final Long BOB_DENSE_CONFIG_ID = 99784L;

    private String token;
    private Long d1;
    private Long d9;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM dataset_parse_config");
        jdbcTemplate.update("DELETE FROM llm_user_config WHERE user_id IN (?, ?)", ALICE_ID, BOB_ID);
        jdbcTemplate.update("DELETE FROM dataset WHERE user_id IN (?, ?)", ALICE_ID, BOB_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ALICE_ID, BOB_ID);

        insertUser(ALICE_ID, "pc_alice");
        insertUser(BOB_ID, "pc_bob");
        insertEmbeddingConfig(ALICE_SPARSE_CONFIG_ID, ALICE_ID, "alice-sparse", "SPARSE_EMBEDDING");
        insertEmbeddingConfig(ALICE_DENSE_CONFIG_ID, ALICE_ID, "alice-dense", "EMBEDDING");
        insertEmbeddingConfig(BOB_SPARSE_CONFIG_ID, BOB_ID, "bob-sparse", "SPARSE_EMBEDDING");
        insertEmbeddingConfig(BOB_DENSE_CONFIG_ID, BOB_ID, "bob-dense", "EMBEDDING");

        jdbcTemplate.update("INSERT INTO dataset (user_id, name, status) VALUES (?, 'PC配置-D1', 'ACTIVE')", ALICE_ID);
        d1 = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = 'PC配置-D1'", Long.class, ALICE_ID);
        jdbcTemplate.update("INSERT INTO dataset (user_id, name, status) VALUES (?, 'PC配置-D9', 'ACTIVE')", BOB_ID);
        d9 = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = 'PC配置-D9'", Long.class, BOB_ID);

        StpUtil.login(ALICE_ID);
        token = StpUtil.getTokenValue();
    }

    @BeforeEach
    void cleanConfig() {
        // 每个用例独立的配置状态：清空配置表（数据集行保留）。
        jdbcTemplate.update("DELETE FROM dataset_parse_config");
    }

    private void insertUser(Long id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname(name);
        user.setEmail(name + "@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    private void insertEmbeddingConfig(Long id, Long userId, String modelName, String capability) {
        jdbcTemplate.update("""
            INSERT INTO llm_user_config (
                id, user_id, provider_id, provider_type, api_key, api_base_url, protocol,
                model_name, capability, is_active, is_default, is_system_preset
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, false, false)
            """, id, userId, 1L, "aliyun", "encrypted-key",
            "https://example.com/embeddings", "openai", modelName, capability);
    }

    private Integer configCount(Long datasetId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dataset_parse_config WHERE dataset_id = ?", Integer.class, datasetId);
    }

    private String column(String col, Long datasetId) {
        return jdbcTemplate.queryForObject(
            "SELECT " + col + " FROM dataset_parse_config WHERE dataset_id = ?", String.class, datasetId);
    }

    private void putOk(Long datasetId, String body) throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", datasetId)
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings(body)))
            .andExpect(status().isOk());
    }

    private String withBindings(String body) {
        String trimmed = body.trim();
        String prefix = "\"sparse_embedding_config_id\":" + ALICE_SPARSE_CONFIG_ID
            + ",\"dense_embedding_config_id\":" + ALICE_DENSE_CONFIG_ID;
        if ("{}".equals(trimmed)) {
            return "{" + prefix + "}";
        }
        return trimmed.replaceFirst("\\{", "{" + prefix + ",");
    }

    // ===== 创建期 =====

    @Test
    @DisplayName("创建数据集写入默认召回配置行")
    void Should_WriteDefaultRecallConfig_When_CreateDataset() throws Exception {
        mockMvc.perform(post("/api/v1/datasets")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"建集默认配置\",\"description\":\"\","
                    + "\"sparse_embedding_config_id\":" + ALICE_SPARSE_CONFIG_ID + ","
                    + "\"dense_embedding_config_id\":" + ALICE_DENSE_CONFIG_ID + "}"))
            .andExpect(status().isOk());

        Long newId = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = '建集默认配置'", Long.class, ALICE_ID);
        assertThat(configCount(newId)).isEqualTo(1);
        assertThat(column("recall_config", newId))
            .contains("recall_enabled_sources")
            .contains("rerank_top_n")
            .contains("recall_strict");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT sparse_embedding_config_id FROM dataset_parse_config WHERE dataset_id = ?",
            Long.class, newId)).isEqualTo(ALICE_SPARSE_CONFIG_ID);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT dense_embedding_config_id FROM dataset_parse_config WHERE dataset_id = ?",
            Long.class, newId)).isEqualTo(ALICE_DENSE_CONFIG_ID);
    }

    // ===== 读取 =====

    @Test
    @DisplayName("读取已配置数据集回显已存内容")
    void Should_EchoStored_When_GetExistingConfig() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32,\"max_chunk_tokens\":768,"
            + "\"hard_max_tokens\":1400,\"stage_two_algorithm\":\" Semantic_Depth_Window \","
            + "\"protected_neighbor_overlap\":true}}");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").value(32))
            .andExpect(jsonPath("$.data.chunking.max_chunk_tokens").value(768))
            .andExpect(jsonPath("$.data.chunking.hard_max_tokens").value(1400))
            .andExpect(jsonPath("$.data.chunking.stage_two_algorithm").value("semantic_depth_window"))
            .andExpect(jsonPath("$.data.chunking.protected_neighbor_overlap").value(true));
    }

    @Test
    @DisplayName("读取无配置行返回未配置且不落库")
    void Should_ReturnEmptyAndNotPersist_When_GetWithoutRow() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunking").exists())
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").doesNotExist())
            .andExpect(jsonPath("$.data.recall.recall_result_limit").doesNotExist())
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[0]").value("bm25"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[1]").value("sparse"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[2]").value("dense"))
            .andExpect(jsonPath("$.data.recall.rerank_top_n").value(8))
            .andExpect(jsonPath("$.data.recall.recall_strict").value(false));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("读取旧 recall JSON 缺失新增项时回落默认")
    void Should_FillRecallDefaults_When_GetOldRecallJson() throws Exception {
        putOk(d1, "{\"recall\":{\"recall_result_limit\":33,\"recall_context_token_budget\":4000,"
            + "\"sparse_top_k\":10,\"sparse_score_threshold\":0.0,\"dense_top_k\":5,\"dense_score_threshold\":0.5}}");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recall.recall_result_limit").value(33))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[0]").value("bm25"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[1]").value("sparse"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[2]").value("dense"))
            .andExpect(jsonPath("$.data.recall.rerank_top_n").value(8))
            .andExpect(jsonPath("$.data.recall.recall_strict").value(false));
    }

    // ===== 更新：PUT 全量覆盖 =====

    @Test
    @DisplayName("无配置行 PUT 全量则建行写入四类")
    void Should_CreateRow_When_PutWithoutRow() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":64},\"enhancement\":{\"enable_table_enhancement\":true},"
            + "\"pdf\":{\"pdf_parser_backend\":\"naive\"},\"recall\":{\"recall_result_limit\":15}}");

        assertThat(configCount(d1)).isEqualTo(1);
        assertThat(column("pdf_config", d1)).contains("naive");
        assertThat(column("recall_config", d1)).contains("15");
    }

    @Test
    @DisplayName("PUT 全量覆盖整行被新内容整体替换")
    void Should_OverwriteWholeRow_When_PutAgain() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":48},\"recall\":{\"recall_result_limit\":20}}");
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":16},\"recall\":{\"recall_result_limit\":50}}");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").value(16))
            .andExpect(jsonPath("$.data.recall.recall_result_limit").value(50));
    }

    @Test
    @DisplayName("后端原样存储提交内容不补字段默认")
    void Should_StoreAsIs_When_PartialFieldsSubmitted() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":16}}");

        String chunking = column("chunking_config", d1);
        assertThat(chunking).contains("overlap_tokens");
        assertThat(chunking).doesNotContain("heading_break_level");
        assertThat(chunking).doesNotContain("min_candidate_chunk_tokens");
        assertThat(chunking).doesNotContain("max_chunk_tokens");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").value(16))
            .andExpect(jsonPath("$.data.chunking.heading_break_level").doesNotExist());
    }

    @Test
    @DisplayName("更新后立即读取回显最新值")
    void Should_ReturnLatest_When_GetAfterPut() throws Exception {
        putOk(d1, "{\"recall\":{\"recall_result_limit\":33,\"recall_context_token_budget\":4000,"
            + "\"sparse_top_k\":10,\"sparse_score_threshold\":0.0,\"dense_top_k\":5,\"dense_score_threshold\":0.5}}");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(jsonPath("$.data.recall.dense_top_k").value(5))
            .andExpect(jsonPath("$.data.recall.dense_score_threshold").value(0.5));
    }

    @Test
    @DisplayName("更新配置刷新 updated_at")
    void Should_RefreshUpdatedAt_When_Put() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");
        // 人为把 updated_at 置为很早的时间，模拟「上一次更新」；再 PUT 应被 DB ON UPDATE 刷新而非停留
        jdbcTemplate.update(
            "UPDATE dataset_parse_config SET updated_at = '2000-01-01 00:00:00' WHERE dataset_id = ?", d1);
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":48}}");

        String updatedAt = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM dataset_parse_config WHERE dataset_id = ?", String.class, d1);
        assertThat(updatedAt).isNotNull();
        assertThat(updatedAt).doesNotStartWith("2000");
    }

    // ===== 写入校验 =====

    @Test
    @DisplayName("关键数值字段超范围被拒绝且不落库")
    void Should_Reject_When_KeyFieldOutOfRange() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");

        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"overlap_tokens\":65}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("overlap_tokens")));
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"overlap_tokens\":-1}}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"min_candidate_chunk_tokens\":127}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("min_candidate_chunk_tokens")));
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"min_candidate_chunk_tokens\":257}}"))
            .andExpect(status().isBadRequest());

        assertThat(column("chunking_config", d1)).contains("32");
    }

    @Test
    @DisplayName("pdf_parser_backend 取值白名单校验")
    void Should_ValidateWhitelist_When_PutPdfBackend() throws Exception {
        for (String ok : new String[] {"auto", "mineru", "opendataloader", "naive"}) {
            mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(withBindings("{\"pdf\":{\"pdf_parser_backend\":\"" + ok + "\"}}")))
                .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pdf\":{\"pdf_parser_backend\":\"unknown\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("pdf_parser_backend")));
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pdf\":{\"pdf_parser_backend\":\"\"}}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JSON 字段类型非法被拒绝")
    void Should_Reject_When_FieldTypeInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content(withBindings("{\"chunking\":{\"overlap_tokens\":\"abc\"}}")))
            .andExpect(status().isBadRequest());
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("recall 正整数项非正被拒绝")
    void Should_Reject_When_RecallPositiveFieldNotPositive() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"recall\":{\"dense_top_k\":0}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("dense_top_k")));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("recall 新增项合法值被保存并归一化")
    void Should_SaveNormalizedNewRecallFields_When_PutValidRecall() throws Exception {
        putOk(d1, "{\"recall\":{\"recall_result_limit\":20,\"bm25_top_k\":30,"
            + "\"sparse_top_k\":10,\"sparse_score_threshold\":0.1,"
            + "\"dense_top_k\":12,\"dense_score_threshold\":0.2,"
            + "\"recall_enabled_sources\":[\" DENSE \",\"\",\"bm25\",\"dense\"],"
            + "\"recall_fusion_strategy\":\" Weighted_Score \","
            + "\"fusion_bm25_weight\":1.0,\"fusion_sparse_weight\":0.8,\"fusion_dense_weight\":1.2,"
            + "\"rerank_top_n\":3,\"recall_strict\":true}}");

        String recall = column("recall_config", d1);
        assertThat(recall).contains("dense").contains("bm25").doesNotContain(" DENSE ");
        assertThat(recall).contains("weighted_score").contains("fusion_bm25_weight");
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recall.bm25_top_k").value(30))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[0]").value("dense"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[1]").value("bm25"))
            .andExpect(jsonPath("$.data.recall.recall_enabled_sources[2]").doesNotExist())
            .andExpect(jsonPath("$.data.recall.recall_fusion_strategy").value("weighted_score"))
            .andExpect(jsonPath("$.data.recall.fusion_dense_weight").value(1.2))
            .andExpect(jsonPath("$.data.recall.rerank_top_n").value(3))
            .andExpect(jsonPath("$.data.recall.recall_strict").value(true));
    }

    @Test
    @DisplayName("recall_enabled_sources 含未知值被拒绝")
    void Should_Reject_When_RecallSourceUnknown() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"recall\":{\"recall_enabled_sources\":[\"bm25\",\"unknown\"]}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("recall_enabled_sources")));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("rerank_top_n 非正整数被拒绝")
    void Should_Reject_When_RerankTopNNotPositive() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"recall\":{\"rerank_top_n\":0}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("rerank_top_n")));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("recall_fusion_strategy 未知值被拒绝")
    void Should_Reject_When_RecallFusionStrategyUnknown() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"recall\":{\"recall_fusion_strategy\":\"unknown\"}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("recall_fusion_strategy")));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("recall 融合权重负数被拒绝")
    void Should_Reject_When_RecallFusionWeightNegative() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"recall\":{\"fusion_sparse_weight\":-0.1}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("fusion_sparse_weight")));
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("chunking 跨字段 token 边界非法被拒绝")
    void Should_Reject_When_ChunkBoundsInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"chunking\":{\"min_candidate_chunk_tokens\":256,"
                    + "\"max_chunk_tokens\":512,\"hard_max_tokens\":511}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("hard_max_tokens")));
        assertThat(configCount(d1)).isZero();
    }

    // ===== 增强 =====

    @Test
    @DisplayName("enhancement 承载三个开关")
    void Should_CarryThreeSwitches_When_PutEnhancement() throws Exception {
        putOk(d1, "{\"enhancement\":{\"enable_table_enhancement\":false,"
            + "\"enable_image_enhancement\":true,\"enable_heading_hierarchy\":true}}");
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(jsonPath("$.data.enhancement.enable_table_enhancement").value(false))
            .andExpect(jsonPath("$.data.enhancement.enable_image_enhancement").value(true))
            .andExpect(jsonPath("$.data.enhancement.enable_heading_hierarchy").value(true));
    }

    @Test
    @DisplayName("忽略历史残留的增强模型字段")
    void Should_IgnoreLegacyModelFields_When_PutEnhancement() throws Exception {
        putOk(d1, "{\"enhancement\":{\"enable_table_enhancement\":true,\"table_model\":\"gpt-4o\",\"vision_model\":\"x\"}}");
        String enhancement = column("enhancement_config", d1);
        assertThat(enhancement).contains("enable_table_enhancement");
        assertThat(enhancement).doesNotContain("table_model");
        assertThat(enhancement).doesNotContain("vision_model");
    }

    @Test
    @DisplayName("开启增强但用户未配默认模型后端不阻断")
    void Should_NotBlock_When_EnhancementOnWithoutModel() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withBindings("{\"enhancement\":{\"enable_table_enhancement\":true}}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enhancement.enable_table_enhancement").value(true));
    }

    // ===== 向量模型绑定 =====

    @Test
    @DisplayName("已绑定向量模型后再次 PUT 不能重绑")
    void Should_Reject_When_RebindEmbeddingConfig() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");

        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sparse_embedding_config_id\":" + ALICE_SPARSE_CONFIG_ID
                    + ",\"dense_embedding_config_id\":" + BOB_DENSE_CONFIG_ID
                    + ",\"chunking\":{\"overlap_tokens\":48}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("dense_embedding_config_id")));

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dense_embedding_config_id").value(ALICE_DENSE_CONFIG_ID))
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").value(32));
    }

    // ===== 权限与登录 =====

    @Test
    @DisplayName("读取他人数据集配置被拒绝")
    void Should_Return404_When_GetOthersConfig() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d9).header("satoken", token))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("更新他人数据集配置被拒绝")
    void Should_Return404AndNotPersist_When_PutOthersConfig() throws Exception {
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d9).header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"overlap_tokens\":10}}"))
            .andExpect(status().isNotFound());
        assertThat(configCount(d9)).isZero();
    }

    @Test
    @DisplayName("未登录访问被拒绝")
    void Should_Return401_When_NoLogin() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/datasets/{id}/parse-config", d1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"overlap_tokens\":10}}"))
            .andExpect(status().isUnauthorized());
    }

    // ===== 幂等 =====

    @Test
    @DisplayName("重复 PUT 相同配置结果幂等")
    void Should_BeIdempotent_When_PutSameTwice() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");
        assertThat(configCount(d1)).isEqualTo(1);
        assertThat(column("chunking_config", d1)).contains("32");
    }
}
