package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "tolink.oss.file-root-path=/tmp/tolink-knowledge-file-test",
    "tolink.knowledge-file.max-size-bytes=64",
    "tolink.knowledge-file.internal-base-url=http://tolink-service:8080",
    "tolink.knowledge-file.service-token=test-service-token"
})
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, KnowledgeFileControllerTest.KnowledgeFileTestConfig.class})
class KnowledgeFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingMQSend recordingMQSend;

    @SpyBean
    private com.qingluo.link.components.oss.service.IOssService ossService;

    private String token;
    private Long userId;
    private Long datasetId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM knowledge_file_config");
        jdbcTemplate.update("DELETE FROM document_parsed_file");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");
        recordingMQSend.clear();

        String username = "knowledge_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 1)
                """, username, passwordEncoder.encode("password123"), "知识文件测试", username + "@test.com");
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);

        jdbcTemplate.update("""
                INSERT INTO dataset (user_id, name, description, status)
                VALUES (?, ?, '知识文件测试数据集', 'ACTIVE')
                """, userId, "dataset_" + System.nanoTime());
        datasetId = jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ?", Long.class, userId);

        StpUtil.login(userId);
        token = StpUtil.getTokenValue();
    }

    @Test
    void Should_UploadPrivateKnowledgeFileAndCreateDatabaseRecord_When_FileIsSupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guide.MD", "text/markdown", "# hello".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .param("parseImmediately", "false")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.datasetId").value(datasetId))
            .andExpect(jsonPath("$.data.originalFilename").value("guide.MD"))
            .andExpect(jsonPath("$.data.fileSuffix").value("md"))
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOAD_SUCCESS"))
            .andExpect(jsonPath("$.data.isUploadSuccess").value(true))
            .andExpect(jsonPath("$.data.parseNoticeStatus").value("PARSE_NOTICE_PENDING"))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Long fileId = data.get("id").asLong();

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE id = ? AND dataset_id = ? AND user_id = ? AND upload_status = 'success'
                """, Integer.class, fileId, datasetId, userId);
        assertThat(count).isEqualTo(1);

        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);
        String bucketName = jdbcTemplate.queryForObject(
            "SELECT bucket_name FROM document_original_file WHERE id = ?", String.class, fileId);
        LocalDate today = LocalDate.now();
        assertThat(objectKey).isEqualTo("%d/%d/%04d/%02d/%02d/%s".formatted(
            userId, datasetId, today.getYear(), today.getMonthValue(), today.getDayOfMonth(), "guide.MD"));
        assertThat(bucketName).isEqualTo("local-private");
    }

    @Test
    void Should_RejectKnowledgeFileUpload_When_SameConversationAlreadyHasSameOriginalFilename() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# first".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# second".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(firstFile)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(secondFile)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("当前数据集下已存在同名原文件，请先重命名后再上传"));

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, Integer.class, datasetId, userId, "duplicate.md");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void Should_RejectKnowledgeFileUpload_When_FileSuffixIsUnsupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "virus.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "bad".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_RejectKnowledgeFileUpload_When_FileNameContainsIllegalCharacters() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad#name.md", "text/markdown", "# bad".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("文件名包含非法字符"));
    }

    @Test
    void Should_ThrowException_When_OssUploadFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "failed.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes(StandardCharsets.UTF_8));
        willReturn("").given(ossService).upload2PreviewUrl(any(), any(), anyString());

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("文件上传失败，请稍后重试"));

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, Integer.class, datasetId, userId, "failed.txt");
        assertThat(count).isEqualTo(1);

        String uploadStatus = jdbcTemplate.queryForObject("""
                SELECT upload_status FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, String.class, datasetId, userId, "failed.txt");
        Boolean isUploadSuccess = jdbcTemplate.queryForObject("""
                SELECT is_upload_success FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, Boolean.class, datasetId, userId, "failed.txt");
        String failureReason = jdbcTemplate.queryForObject("""
                SELECT failure_reason FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, String.class, datasetId, userId, "failed.txt");
        assertThat(uploadStatus).isEqualTo("failed");
        assertThat(isUploadSuccess).isFalse();
        assertThat(failureReason).isEqualTo("文件上传失败，请稍后重试");
    }

    @Test
    void Should_RejectKnowledgeFileUpload_When_DatabaseConfigOverridesMaxSize() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO knowledge_file_config (max_size_bytes, allowed_suffixes, updated_by)
                VALUES (?, ?, ?)
                """, 5L, "[\"txt\"]", 99995L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "too-large.txt", MediaType.TEXT_PLAIN_VALUE, "123456".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_RejectKnowledgeFileUpload_When_DatabaseConfigOverridesAllowedSuffixes() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO knowledge_file_config (max_size_bytes, allowed_suffixes, updated_by)
                VALUES (?, ?, ?)
                """, 1024L, "[\"pdf\"]", 99995L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "note.txt", MediaType.TEXT_PLAIN_VALUE, "ok".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_ListAndDeleteKnowledgeFile_When_FileBelongsToCurrentUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "note.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);
        Path privateFile = Path.of("/tmp/tolink-knowledge-file-test/private").resolve(objectKey);
        assertThat(Files.exists(privateFile)).isTrue();

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(fileId));

        mockMvc.perform(delete("/api/v1/knowledge-files/{fileId}", fileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertThat(Files.exists(privateFile)).isFalse();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE id = ?", Integer.class, fileId);
        assertThat(count).isEqualTo(0);
        Integer parsedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_parsed_file WHERE document_original_file_id = ?", Integer.class, fileId);
        assertThat(parsedCount).isEqualTo(0);

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void Should_Allow_ReuploadSameOriginalFilename_After_Delete() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "repeat.txt", MediaType.TEXT_PLAIN_VALUE, "first".getBytes(StandardCharsets.UTF_8));

        MvcResult firstUpload = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(firstUpload.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/knowledge-files/{fileId}", fileId)
                .header("satoken", token))
            .andExpect(status().isOk());

        MockMultipartFile secondFile = new MockMultipartFile(
            "file", "repeat.txt", MediaType.TEXT_PLAIN_VALUE, "second".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(secondFile)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void Should_Enforce_UniqueConstraint_For_UndeletedSameOriginalFilename() {
        jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                    upload_status, is_upload_success, parse_notice_status, parse_task_id, parse_notice_retry_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            datasetId, userId, "unique.txt", "txt", 1L, "local-private",
            "success", true, "pending", "task-" + System.nanoTime(), 0);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                    upload_status, is_upload_success, parse_notice_status, parse_task_id, parse_notice_retry_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            datasetId, userId, "unique.txt", "txt", 1L, "local-private",
            "success", true, "pending", "task-" + System.nanoTime(), 0))
            .isInstanceOf(Exception.class);
    }

    @Test
    void Should_DownloadOriginalFileThroughInternalEndpoint_When_ServiceTokenAndTaskMatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "private.txt", MediaType.TEXT_PLAIN_VALUE, "secret-content".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        String taskId = jdbcTemplate.queryForObject(
            "SELECT parse_task_id FROM document_original_file WHERE id = ?", String.class, fileId);

        mockMvc.perform(get("/api/v1/internal/knowledge-files/{fileId}/content", fileId)
                .param("taskId", taskId)
                .header("Authorization", "Bearer test-service-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("secret-content"));
    }

    @Test
    void Should_RejectInternalDownload_When_ServiceTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/internal/knowledge-files/{fileId}/content", 1L)
                .param("taskId", "wrong")
                .header("Authorization", "Bearer wrong"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void Should_CreateParseTaskAndSendMqMessage_When_UploadWithParseImmediately() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "parse.md", "text/markdown", "# parse".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .param("parseImmediately", "true")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOAD_SUCCESS"))
            .andExpect(jsonPath("$.data.parseNoticeStatus").value("PARSE_NOTICE_SENT"))
            .andExpect(jsonPath("$.data.parseStatus").value("PENDING"))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Long fileId = data.get("id").asLong();
        String parseTaskId = data.get("parseTaskId").asText();
        assertThat(parseTaskId).isNotBlank();
        assertThat(recordingMQSend.messages()).hasSize(1);
        assertThat(recordingMQSend.messages().get(0).getMQName()).isEqualTo("tolink.rag.parse_task");
        assertThat(recordingMQSend.messages().get(0).getMessage()).contains("\"document_id\":\"" + fileId + "\"");
        String parseStatus = jdbcTemplate.queryForObject(
            "SELECT parse_status FROM document_parsed_file WHERE document_original_file_id = ?",
            String.class, fileId);
        assertThat(parseStatus).isEqualTo("pending");
    }

    @Test
    void Should_CreateParseTaskAndSendMqMessage_When_UserStartsParseAfterUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "later.txt", MediaType.TEXT_PLAIN_VALUE, "parse later".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/knowledge-files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parseNoticeStatus").value("PARSE_NOTICE_PENDING"))
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        MvcResult result = mockMvc.perform(post("/api/v1/knowledge-files/{fileId}/parse-tasks", fileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parseNoticeStatus").value("PARSE_NOTICE_SENT"))
            .andExpect(jsonPath("$.data.parseStatus").value("PENDING"))
            .andReturn();

        String parseTaskId = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("data").get("parseTaskId").asText();
        assertThat(parseTaskId).isNotBlank();
        assertThat(recordingMQSend.messages()).hasSize(1);
        String parseStatus = jdbcTemplate.queryForObject(
            "SELECT parse_status FROM document_parsed_file WHERE document_original_file_id = ?",
            String.class, fileId);
        assertThat(parseStatus).isEqualTo("pending");
    }

    @TestConfiguration
    static class KnowledgeFileTestConfig {

        @Bean
        RecordingMQSend recordingMQSend() {
            return new RecordingMQSend();
        }
    }

    static class RecordingMQSend implements MQSend {

        private final List<AbstractMQ> messages = new ArrayList<>();

        @Override
        public void send(AbstractMQ abstractMQ) {
            messages.add(abstractMQ);
        }

        @Override
        public void send(AbstractMQ abstractMQ, int delay) {
            messages.add(abstractMQ);
        }

        List<AbstractMQ> messages() {
            return messages;
        }

        void clear() {
            messages.clear();
        }
    }
}
