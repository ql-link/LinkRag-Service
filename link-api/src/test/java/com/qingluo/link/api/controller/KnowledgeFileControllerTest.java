package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.qingluo.link.service.KnowledgeFileService;

@SpringBootTest(properties = {
    "tolink.oss.file-root-path=/tmp/tolink-knowledge-file-test",
    "tolink.knowledge-file.max-size-bytes=64",
    "tolink.knowledge-file.internal-base-url=http://tolink-service:8080",
    "tolink.knowledge-file.service-token=test-service-token",
    "qingluopay.mq.vender=none"
})
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
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
    private KnowledgeFileService knowledgeFileService;

    @SpyBean
    private com.qingluo.link.components.oss.service.IOssService ossService;

    private String token;
    private Long userId;
    private Long datasetId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM knowledge_file_config");
        jdbcTemplate.update("DELETE FROM document_parsed_file");
        jdbcTemplate.update("DELETE FROM document_parse_task");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");
        deleteDirectory(Path.of("/tmp/tolink-knowledge-file-test"));

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
    void Should_UploadOriginalFileToRagRaw_When_FileIsValid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "Guide.MD", "text/markdown", "# hello".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .param("parseImmediately", "true")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.datasetId").value(datasetId))
            .andExpect(jsonPath("$.data.originalFilename").value("Guide.MD"))
            .andExpect(jsonPath("$.data.fileSuffix").value("md"))
            .andExpect(jsonPath("$.data.uploadStatus").value("success"))
            .andExpect(jsonPath("$.data.isUploadSuccess").value(true))
            .andReturn();

        Long fileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);
        String bucketName = jdbcTemplate.queryForObject(
            "SELECT bucket_name FROM document_original_file WHERE id = ?", String.class, fileId);
        LocalDate today = LocalDate.now();

        assertThat(objectKey).isEqualTo("original/user-%d/dataset-%d/%04d/%02d/%02d/%d/%s".formatted(
            userId, datasetId, today.getYear(), today.getMonthValue(), today.getDayOfMonth(), fileId, "Guide.MD"));
        assertThat(bucketName).isEqualTo("rag-raw");
        assertThat(Files.readString(Path.of("/tmp/tolink-knowledge-file-test/private").resolve(objectKey)))
            .isEqualTo("# hello");
        Integer taskCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_parse_task WHERE document_original_file_id = ?", Integer.class, fileId);
        assertThat(taskCount).isEqualTo(1);
    }

    @Test
    void Should_UseUploadThreadPool_When_UploadingOriginalFile() throws Exception {
        AtomicReference<String> uploadThreadName = new AtomicReference<>();
        doAnswer(invocation -> {
            uploadThreadName.set(Thread.currentThread().getName());
            return invocation.callRealMethod();
        }).when(ossService).upload2PreviewUrl(any(), any(), anyString());

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(new MockMultipartFile("file", "thread.txt", MediaType.TEXT_PLAIN_VALUE,
                    "thread-content".getBytes(StandardCharsets.UTF_8)))
                .header("satoken", token))
            .andExpect(status().isOk());

        assertThat(uploadThreadName.get()).startsWith("knowledge-file-upload-");
    }

    @Test
    void Should_RejectUpload_When_SameUserDatasetFilenameAndSuffixAlreadySucceeded() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# first".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# second".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(firstFile)
                .header("satoken", token))
            .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(secondFile)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("当前数据集下已存在同名同后缀原文件"));

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ? AND file_suffix = ?
                """, Integer.class, datasetId, userId, "duplicate.md", "md");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void Should_ReuseFailedRecordAndObjectKey_When_RetryUploadSucceeds() throws Exception {
        Long fileId = insertOriginalFile("retry.txt", "txt", "failed", false,
            "original/user-%d/dataset-%d/2026/04/25/123/retry.txt".formatted(userId, datasetId));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(new MockMultipartFile("file", "retry.txt", MediaType.TEXT_PLAIN_VALUE,
                    "second-content".getBytes(StandardCharsets.UTF_8)))
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(fileId))
            .andExpect(jsonPath("$.data.uploadStatus").value("success"));

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ? AND file_suffix = ?
                """, Integer.class, datasetId, userId, "retry.txt", "txt");
        String status = jdbcTemplate.queryForObject(
            "SELECT upload_status FROM document_original_file WHERE id = ?", String.class, fileId);
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);

        assertThat(count).isEqualTo(1);
        assertThat(status).isEqualTo("success");
        assertThat(objectKey).isEqualTo("original/user-%d/dataset-%d/2026/04/25/123/retry.txt".formatted(userId, datasetId));
        assertThat(Files.readString(Path.of("/tmp/tolink-knowledge-file-test/private").resolve(objectKey)))
            .isEqualTo("second-content");
    }

    @Test
    void Should_ConvertExpiredUploadingRecordToFailed_When_TimeoutCompensationRuns() {
        Long fileId = insertOriginalFile("timeout.txt", "txt", "uploading", false,
            "original/user-%d/dataset-%d/2026/04/25/123/timeout.txt".formatted(userId, datasetId));
        jdbcTemplate.update("UPDATE document_original_file SET updated_at = ? WHERE id = ?",
            LocalDateTime.now().minusMinutes(2), fileId);

        knowledgeFileService.markTimeoutUploadsFailed();

        String status = jdbcTemplate.queryForObject(
            "SELECT upload_status FROM document_original_file WHERE id = ?", String.class, fileId);
        Boolean success = jdbcTemplate.queryForObject(
            "SELECT is_upload_success FROM document_original_file WHERE id = ?", Boolean.class, fileId);
        String reason = jdbcTemplate.queryForObject(
            "SELECT failure_reason FROM document_original_file WHERE id = ?", String.class, fileId);

        assertThat(status).isEqualTo("failed");
        assertThat(success).isFalse();
        assertThat(reason).isEqualTo("上传超时，请重新上传");
    }

    @Test
    void Should_MarkRecordFailed_When_OssUploadFails() throws Exception {
        willReturn("").given(ossService).upload2PreviewUrl(any(), any(), anyString());

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(new MockMultipartFile("file", "failed.txt", MediaType.TEXT_PLAIN_VALUE,
                    "hello".getBytes(StandardCharsets.UTF_8)))
                .header("satoken", token))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("文件上传失败，请稍后重试"));

        String uploadStatus = jdbcTemplate.queryForObject("""
                SELECT upload_status FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ? AND file_suffix = ?
                """, String.class, datasetId, userId, "failed.txt", "txt");
        assertThat(uploadStatus).isEqualTo("failed");
    }

    @Test
    void Should_ListDetailAndDeleteUploadedFile_When_FileBelongsToCurrentUser() throws Exception {
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(new MockMultipartFile("file", "note.txt", MediaType.TEXT_PLAIN_VALUE,
                    "hello".getBytes(StandardCharsets.UTF_8)))
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);
        Path privateFile = Path.of("/tmp/tolink-knowledge-file-test/private").resolve(objectKey);
        assertThat(Files.exists(privateFile)).isTrue();

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files", datasetId).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(fileId));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId).header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(fileId));

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId).header("satoken", token))
            .andExpect(status().isOk());

        assertThat(Files.exists(privateFile)).isFalse();
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE id = ?", Integer.class, fileId);
        assertThat(count).isZero();
    }

    @Test
    void Should_CreateParseTask_When_UserSubmitsManualParse() throws Exception {
        Long fileId = insertOriginalFile("manual.pdf", "pdf", "success", true,
            "original/user-%d/dataset-%d/2026/04/26/123/manual.pdf".formatted(userId, datasetId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileId").value(fileId))
            .andExpect(jsonPath("$.data.frontendStatus").value("parse_waiting"));

        String taskStatus = jdbcTemplate.queryForObject(
            "SELECT task_status FROM document_parse_task WHERE document_original_file_id = ?", String.class, fileId);
        Integer retryCount = jdbcTemplate.queryForObject(
            "SELECT dispatch_retry_count FROM document_parse_task WHERE document_original_file_id = ?",
            Integer.class, fileId);
        assertThat(taskStatus).isEqualTo("created");
        assertThat(retryCount).isEqualTo(1);
    }

    @Test
    void Should_RejectManualParse_When_FileAlreadyHasRunningTask() throws Exception {
        Long fileId = insertOriginalFile("running.txt", "txt", "success", true,
            "original/user-%d/dataset-%d/2026/04/26/123/running.txt".formatted(userId, datasetId));
        insertParseTask(fileId, "running-task", "created", null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("文件正在解析中，请勿重复提交"));
    }

    @Test
    void Should_ReturnAllFileParseResults_When_QueryByFileList() throws Exception {
        Long successFileId = insertOriginalFile("ok.pdf", "pdf", "success", true,
            "original/user-%d/dataset-%d/2026/04/26/123/ok.pdf".formatted(userId, datasetId));
        Long failedFileId = insertOriginalFile("bad.pdf", "pdf", "success", true,
            "original/user-%d/dataset-%d/2026/04/26/123/bad.pdf".formatted(userId, datasetId));
        insertParseTask(successFileId, "success-task", "success", null);
        insertParseTask(failedFileId, "failed-task", "failed", "格式暂不支持");
        jdbcTemplate.update("""
                INSERT INTO document_parsed_file (
                    document_original_file_id, dataset_id, user_id, latest_success_task_id,
                    original_filename, parsed_filename, parsed_bucket_name, parsed_object_key,
                    parsed_storage_path, parse_count, parsed_at
                ) VALUES (?, ?, ?, ?, 'ok.pdf', 'ok.md', 'rag-md', 'parsed/ok.md',
                    'rag-md/parsed/ok.md', 1, CURRENT_TIMESTAMP)
                """, successFileId, datasetId, userId, "success-task");

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files/parse-results", datasetId)
                .param("fileIds", successFileId + "," + failedFileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].fileId").value(successFileId))
            .andExpect(jsonPath("$.data[0].frontendStatus").value("parse_success"))
            .andExpect(jsonPath("$.data[0].parsedFilename").value("ok.md"))
            .andExpect(jsonPath("$.data[1].fileId").value(failedFileId))
            .andExpect(jsonPath("$.data[1].frontendStatus").value("parse_failed"))
            .andExpect(jsonPath("$.data[1].failureReason").value("格式暂不支持"));
    }

    private Long insertOriginalFile(String filename, String suffix, String status, boolean success, String objectKey) {
        jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, content_type,
                    bucket_name, object_key, upload_status, is_upload_success, failure_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, datasetId, userId, filename, suffix, 1L, "text/plain",
            "rag-raw", objectKey, status, success, success ? null : "旧失败原因");
        return jdbcTemplate.queryForObject("""
                SELECT id FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ? AND file_suffix = ?
                """, Long.class, datasetId, userId, filename, suffix);
    }

    private void insertParseTask(Long fileId, String taskId, String status, String failureReason) {
        jdbcTemplate.update("""
                INSERT INTO document_parse_task (
                    task_id, document_original_file_id, dataset_id, user_id,
                    trigger_mode, task_status, failure_reason
                ) VALUES (?, ?, ?, ?, 'manual_retry', ?, ?)
                """, taskId, fileId, datasetId, userId, status, failureReason);
    }

    private void deleteDirectory(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                .forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    } catch (Exception ignored) {
                    }
                });
        }
    }
}
