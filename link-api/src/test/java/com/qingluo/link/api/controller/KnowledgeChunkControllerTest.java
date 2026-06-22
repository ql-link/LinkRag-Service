package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import org.junit.jupiter.api.BeforeAll;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KnowledgeChunkController 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeChunkControllerTest {

    private static final Long USER_ID = 99881L;
    private static final Long OTHER_USER_ID = 99882L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private Long datasetId;
    private Long firstFileId;
    private Long secondFileId;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM kb_document_chunk");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        insertUser(USER_ID, "chunk-user");
        insertUser(OTHER_USER_ID, "chunk-other");

        jdbcTemplate.update("INSERT INTO dataset (user_id, name, status) VALUES (?, 'chunk-dataset', 'ACTIVE')", USER_ID);
        datasetId = jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = 'chunk-dataset'",
            Long.class,
            USER_ID);

        firstFileId = insertFile("rag-intro.pdf");
        secondFileId = insertFile("qa-notes.md");
        insertChunk("chunk-a", firstFileId, USER_ID, datasetId, "RAG 是检索增强生成。", "ACTIVE");
        insertChunk("chunk-b", secondFileId, USER_ID, datasetId, "ToLink 支持知识库问答。", "ACTIVE");
        insertChunk("chunk-removed", firstFileId, USER_ID, datasetId, "已删除片段", "REMOVED");
        insertChunk("chunk-other", firstFileId, OTHER_USER_ID, datasetId, "其他用户片段", "ACTIVE");

        StpUtil.login(USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @DisplayName("批量查询 Chunk 详情：按请求顺序返回且过滤不可访问片段")
    void Should_ReturnChunkDetailsInRequestOrder_When_BatchQuery() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/chunks/batch")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "chunkIds": ["chunk-b", "chunk-missing", "chunk-a", "chunk-removed", "chunk-other"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].chunkId").value("chunk-b"))
            .andExpect(jsonPath("$.data[0].documentId").value(secondFileId))
            .andExpect(jsonPath("$.data[0].fileName").value("qa-notes.md"))
            .andExpect(jsonPath("$.data[0].content").value("ToLink 支持知识库问答。"))
            .andExpect(jsonPath("$.data[0].score").doesNotExist())
            .andExpect(jsonPath("$.data[1].chunkId").value("chunk-a"))
            .andExpect(jsonPath("$.data[1].documentId").value(firstFileId))
            .andExpect(jsonPath("$.data[1].fileName").value("rag-intro.pdf"))
            .andExpect(jsonPath("$.data[1].content").value("RAG 是检索增强生成。"));
    }

    @Test
    @DisplayName("批量查询 Chunk 详情：未登录返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/chunks/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chunkIds\":[\"chunk-a\"]}"))
            .andExpect(status().isUnauthorized());
    }

    private void insertUser(Long userId, String username) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname(username);
        user.setEmail(username + "@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    private Long insertFile(String filename) {
        jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                    object_key, upload_status, is_upload_success
                ) VALUES (?, ?, ?, 'md', 128, 'rag-raw', ?, 'success', true)
                """,
            datasetId,
            USER_ID,
            filename,
            "u/" + filename);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM document_original_file WHERE user_id = ? AND original_filename = ?",
            Long.class,
            USER_ID,
            filename);
    }

    private void insertChunk(String chunkId, Long docId, Long userId, Long setId, String content, String lifecycleStatus) {
        jdbcTemplate.update("""
                INSERT INTO kb_document_chunk (
                    chunk_id, doc_id, set_id, user_id, content, content_hash, lifecycle_status,
                    dense_vector_status, sparse_vector_status, es_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCESS', 'SUCCESS', 'SUCCESS')
                """,
            chunkId,
            docId,
            setId,
            userId,
            content,
            "hash-" + chunkId,
            lifecycleStatus);
    }
}
