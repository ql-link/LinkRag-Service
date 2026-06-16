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
 * 并验证 JSON 列经 JacksonTypeHandler 的存取往返（snake_case、不补默认、忽略历史模型字段）。
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

    private String token;
    private Long d1;
    private Long d9;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM dataset_parse_config");
        jdbcTemplate.update("DELETE FROM dataset WHERE user_id IN (?, ?)", ALICE_ID, BOB_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ALICE_ID, BOB_ID);

        insertUser(ALICE_ID, "pc_alice");
        insertUser(BOB_ID, "pc_bob");

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
                .content(body))
            .andExpect(status().isOk());
    }

    // ===== 创建期 =====

    @Test
    @DisplayName("创建数据集不写任何配置行")
    void Should_NotWriteConfig_When_CreateDataset() throws Exception {
        mockMvc.perform(post("/api/v1/datasets")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"建集不写配置\",\"description\":\"\"}"))
            .andExpect(status().isOk());

        Long newId = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = '建集不写配置'", Long.class, ALICE_ID);
        assertThat(configCount(newId)).isZero();
    }

    // ===== 读取 =====

    @Test
    @DisplayName("读取已配置数据集回显已存内容")
    void Should_EchoStored_When_GetExistingConfig() throws Exception {
        putOk(d1, "{\"chunking\":{\"overlap_tokens\":32}}");

        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").value(32));
    }

    @Test
    @DisplayName("读取无配置行返回未配置且不落库")
    void Should_ReturnEmptyAndNotPersist_When_GetWithoutRow() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunking").exists())
            .andExpect(jsonPath("$.data.chunking.overlap_tokens").doesNotExist())
            .andExpect(jsonPath("$.data.recall.recall_result_limit").doesNotExist());
        assertThat(configCount(d1)).isZero();
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
                    .content("{\"pdf\":{\"pdf_parser_backend\":\"" + ok + "\"}}"))
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"chunking\":{\"overlap_tokens\":\"abc\"}}"))
            .andExpect(status().isBadRequest());
        assertThat(configCount(d1)).isZero();
    }

    @Test
    @DisplayName("recall 数值范围不在 Java 端拦截")
    void Should_NotCheckRange_When_PutRecall() throws Exception {
        putOk(d1, "{\"recall\":{\"dense_top_k\":999}}");
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(jsonPath("$.data.recall.dense_top_k").value(999));
    }

    // ===== 增强 =====

    @Test
    @DisplayName("enhancement 仅承载两个开关")
    void Should_CarryTwoSwitches_When_PutEnhancement() throws Exception {
        putOk(d1, "{\"enhancement\":{\"enable_table_enhancement\":false,\"enable_image_enhancement\":true}}");
        mockMvc.perform(get("/api/v1/datasets/{id}/parse-config", d1).header("satoken", token))
            .andExpect(jsonPath("$.data.enhancement.enable_table_enhancement").value(false))
            .andExpect(jsonPath("$.data.enhancement.enable_image_enhancement").value(true));
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
                .content("{\"enhancement\":{\"enable_table_enhancement\":true}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enhancement.enable_table_enhancement").value(true));
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
