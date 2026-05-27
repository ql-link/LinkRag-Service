package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 99989L;

    private String token;
    private Long datasetId;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM chat_message");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername("datasettest");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname("数据集测试");
        user.setEmail("dataset@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("创建数据集")
    void Should_CreateDataset_When_RequestValid() throws Exception {
        String requestJson = """
            {"name":"测试数据集","description":"用于接口测试"}
            """;

        mockMvc.perform(post("/api/v1/datasets")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("测试数据集"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        datasetId = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = ?",
            Long.class,
            TEST_USER_ID,
            "测试数据集"
        );
        assertThat(datasetId).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("查询数据集列表")
    void Should_ReturnDatasetList_When_UserHasDatasets() throws Exception {
        mockMvc.perform(get("/api/v1/datasets")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items[0].id").value(datasetId))
            .andExpect(jsonPath("$.data.items[0].name").value("测试数据集"));
    }

    @Test
    @Order(3)
    @DisplayName("查询数据集详情")
    void Should_ReturnDatasetDetail_When_DatasetOwnedByCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{datasetId}", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(datasetId))
            .andExpect(jsonPath("$.data.name").value("测试数据集"));
    }

    @Test
    @Order(4)
    @DisplayName("删除数据集")
    void Should_DeleteDataset_When_DatasetOwnedByCurrentUser() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO chat_conversation (user_id, dataset_id, title, is_pinned)
            VALUES (?, ?, '删除测试对话', false)
            """, TEST_USER_ID, datasetId);
        Long conversationId = jdbcTemplate.queryForObject(
            "SELECT id FROM chat_conversation WHERE dataset_id = ?",
            Long.class,
            datasetId
        );
        jdbcTemplate.update("""
            INSERT INTO chat_message (conversation_id, role, content, token_count)
            VALUES (?, 'user', 'hello', 1)
            """, conversationId);
        jdbcTemplate.update("""
            INSERT INTO document_original_file (
                dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                upload_status, is_upload_success, parse_notice_status, parse_task_id, parse_status,
                is_parse_success, parse_notice_retry_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, datasetId, TEST_USER_ID, "to-delete.txt", "txt", 1L, "local-private",
            "success", true, "pending", "task-" + System.nanoTime(), "not_started", false, 0);

        mockMvc.perform(delete("/api/v1/datasets/{datasetId}", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        Integer datasetCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dataset WHERE id = ?", Integer.class, datasetId);
        Integer activeConversationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_conversation WHERE dataset_id = ? AND is_deleted = false",
            Integer.class,
            datasetId);
        Integer fileCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE dataset_id = ?", Integer.class, datasetId);
        Integer messageCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_message WHERE conversation_id = ?", Integer.class, conversationId);

        assertThat(datasetCount).isEqualTo(0);
        assertThat(activeConversationCount).isEqualTo(0);
        assertThat(fileCount).isEqualTo(0);
        assertThat(messageCount).isEqualTo(0);
    }
}
