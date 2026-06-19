package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "tolink.oss.file-root-path=/tmp/tolink-document-file-test",
    "tolink.document-file.max-size-bytes=64",
    "tolink.document-file.internal-base-url=http://tolink-service:8080",
    "tolink.document-file.service-token=test-service-token",
    // 用同步执行器覆盖 document-upload 池，使异步上传在请求内完成，集成断言保持确定性。
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, DocumentFileControllerTest.DocumentFileTestConfig.class})
class DocumentFileControllerTest {

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

    @Autowired
    private ControllableUploadExecutor uploadExecutor;

    @SpyBean
    private com.qingluo.link.components.oss.service.IOssService ossService;

    @MockBean
    private DocumentFileConfigCacheService documentFileConfigCacheService;

    private String token;
    private Long userId;
    private Long datasetId;

    @BeforeEach
    void setUp() {
        reset(documentFileConfigCacheService);
        given(documentFileConfigCacheService.getConfig()).willReturn(Optional.empty());
        jdbcTemplate.update("DELETE FROM document_parse_pipeline");
        jdbcTemplate.update("DELETE FROM document_parsed_log");
        jdbcTemplate.update("DELETE FROM document_parse_file");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");
        recordingMQSend.clear();
        uploadExecutor.reset();

        String username = "document_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 1)
                """, username, passwordEncoder.encode("password123"), "文档文件测试", username + "@test.com");
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);

        jdbcTemplate.update("""
                INSERT INTO dataset (user_id, name, description, status)
                VALUES (?, ?, '文档文件测试数据集', 'ACTIVE')
                """, userId, "dataset_" + System.nanoTime());
        datasetId = jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ?", Long.class, userId);

        StpUtil.login(userId);
        token = StpUtil.getTokenValue();
    }

    @Test
    void Should_UploadPrivateDocumentFileAndCreateDatabaseRecord_When_FileIsSupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "guide.MD", "text/markdown", "# hello".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .param("parseImmediately", "false")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.datasetId").value(datasetId))
            .andExpect(jsonPath("$.data.originalFilename").value("guide.MD"))
            .andExpect(jsonPath("$.data.fileSuffix").value("md"))
            // 上传接口立即返回 uploading；终态由异步任务回写（同步执行器下 afterCommit 已完成落库 success）。
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOADING"))
            .andExpect(jsonPath("$.data.isUploadSuccess").value(false))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Long fileId = data.get("id").asLong();

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM document_original_file
                WHERE id = ? AND dataset_id = ? AND user_id = ? AND upload_status = 'success'
                """, Integer.class, fileId, datasetId, userId);
        assertThat(count).isEqualTo(1);
        Integer parseFileCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_parse_file WHERE document_original_file_id = ?", Integer.class, fileId);
        assertThat(parseFileCount).isEqualTo(1);

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
    void Should_RejectDocumentFileUpload_When_SameConversationAlreadyHasSameOriginalFilename() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# first".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondFile = new MockMultipartFile(
            "file", "duplicate.md", "text/markdown", "# second".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(firstFile)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
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
    void Should_RejectDocumentFileUpload_When_FileSuffixIsUnsupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "virus.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "bad".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_RejectDocumentFileUpload_When_FileNameContainsIllegalCharacters() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad#name.md", "text/markdown", "# bad".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
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
        // 异步上传走 File 重载；OSS 返回空 → 异步置 failed（同步执行器下请求返回前已完成）。
        willReturn("").given(ossService)
            .upload2PreviewUrl(any(), any(java.io.File.class), any(), anyString());

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOADING"));

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
    void Should_MarkUploadFailed_When_PoolRejectsTask() throws Exception {
        uploadExecutor.rejectNext();
        MockMultipartFile file = new MockMultipartFile(
            "file", "rejected.txt", MediaType.TEXT_PLAIN_VALUE, "x".getBytes(StandardCharsets.UTF_8));

        // 上传接口仍立即返回 uploading；池满拒绝在 upload() 事务 afterCommit 触发，
        // 置 failed 必须以独立事务（REQUIRES_NEW）提交，否则记录会滞留 uploading。
        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOADING"));

        String uploadStatus = jdbcTemplate.queryForObject("""
                SELECT upload_status FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, String.class, datasetId, userId, "rejected.txt");
        String failureReason = jdbcTemplate.queryForObject("""
                SELECT failure_reason FROM document_original_file
                WHERE dataset_id = ? AND user_id = ? AND original_filename = ?
                """, String.class, datasetId, userId, "rejected.txt");
        assertThat(uploadStatus).isEqualTo("failed");
        assertThat(failureReason).isEqualTo("服务繁忙，请稍后重试");
    }

    @Test
    void Should_RejectDocumentFileUpload_When_RedisConfigOverridesMaxSize() throws Exception {
        given(documentFileConfigCacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(5L, List.of("txt"), 99995L, null)));
        MockMultipartFile file = new MockMultipartFile(
            "file", "too-large.txt", MediaType.TEXT_PLAIN_VALUE, "123456".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_RejectDocumentFileUpload_When_RedisConfigOverridesAllowedSuffixes() throws Exception {
        given(documentFileConfigCacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(1024L, List.of("pdf"), 99995L, null)));
        MockMultipartFile file = new MockMultipartFile(
            "file", "note.txt", MediaType.TEXT_PLAIN_VALUE, "ok".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void Should_ListAndDeleteDocumentFile_When_FileBelongsToCurrentUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "note.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT object_key FROM document_original_file WHERE id = ?", String.class, fileId);
        Path privateFile = Path.of("/tmp/tolink-document-file-test/private").resolve(objectKey);
        assertThat(Files.exists(privateFile)).isTrue();

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(fileId));

        Integer parseFileBefore = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_parse_file WHERE document_original_file_id = ?", Integer.class, fileId);

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 隐性删除：保留 OSS 原文件对象（不物理删、不清缓存）
        assertThat(Files.exists(privateFile)).isTrue();

        // 原文件行软删保留：物理存在但对列表不可见
        Integer physicalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE id = ?", Integer.class, fileId);
        assertThat(physicalCount).isEqualTo(1);
        Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE id = ? AND is_deleted = false", Integer.class, fileId);
        assertThat(activeCount).isEqualTo(0);

        // 解析域交 Python：Java 删除路径不触碰 document_parse_file（计数不变）
        Integer parseFileAfter = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_parse_file WHERE document_original_file_id = ?", Integer.class, fileId);
        assertThat(parseFileAfter).isEqualTo(parseFileBefore);

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void Should_AllowReuploadSameName_AfterSoftDelete() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
            "file", "dup.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes(StandardCharsets.UTF_8));
        MvcResult firstUpload = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(first)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId1 = objectMapper.readTree(firstUpload.getResponse().getContentAsString())
            .get("data").get("id").asLong();

        // 软删
        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId1)
                .header("satoken", token))
            .andExpect(status().isOk());

        // 重传同名：经 @TableLogic 过滤死行 → 走 insert 新行，不被同名校验 / 唯一约束拦截
        MockMultipartFile again = new MockMultipartFile(
            "file", "dup.txt", MediaType.TEXT_PLAIN_VALUE, "hi-again".getBytes(StandardCharsets.UTF_8));
        MvcResult reupload = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(again)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOADING"))
            .andReturn();
        Long fileId2 = objectMapper.readTree(reupload.getResponse().getContentAsString())
            .get("data").get("id").asLong();

        assertThat(fileId2).isNotEqualTo(fileId1);
        // 列表仅见新活行
        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files", datasetId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(fileId2))
            .andExpect(jsonPath("$.data.items[1]").doesNotExist());
        // 物理仍有 2 行（旧死行保留，可追溯）
        Integer physical = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_original_file WHERE dataset_id = ? AND original_filename = ?",
            Integer.class, datasetId, "dup.txt");
        assertThat(physical).isEqualTo(2);
    }

    @Test
    void Should_ListRecentDocumentFilesAcrossDatasets_When_FilesBelongToCurrentUser() throws Exception {
        Long anotherDatasetId = createDataset(userId, "recent_dataset_" + System.nanoTime());
        Long otherUserId = createUser("recent_other_" + System.nanoTime());
        Long otherDatasetId = createDataset(otherUserId, "recent_other_dataset_" + System.nanoTime());

        Long oldFileId = insertDocumentFile(userId, datasetId, "old.txt", "2026-01-01 10:00:00");
        Long firstSameTimeFileId = insertDocumentFile(userId, datasetId, "same-a.txt", "2026-01-02 10:00:00");
        Long secondSameTimeFileId = insertDocumentFile(userId, anotherDatasetId, "same-b.txt", "2026-01-02 10:00:00");
        insertDocumentFile(otherUserId, otherDatasetId, "other-user-newer.txt", "2026-01-03 10:00:00");

        mockMvc.perform(get("/api/v1/files/recent")
                .param("page", "1")
                .param("pageSize", "2")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(2))
            .andExpect(jsonPath("$.data.items[0].id").value(secondSameTimeFileId))
            .andExpect(jsonPath("$.data.items[0].datasetId").value(anotherDatasetId))
            .andExpect(jsonPath("$.data.items[1].id").value(firstSameTimeFileId));

        mockMvc.perform(get("/api/v1/files/recent")
                .param("page", "2")
                .param("pageSize", "2")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(oldFileId))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void Should_ReturnEmptyRecentDocumentList_When_CurrentUserHasNoDocumentFiles() throws Exception {
        Long emptyUserId = createUser("recent_empty_" + System.nanoTime());
        StpUtil.login(emptyUserId);
        String emptyToken = StpUtil.getTokenValue();

        mockMvc.perform(get("/api/v1/files/recent")
                .header("satoken", emptyToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(5))
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void Should_Allow_ReuploadSameOriginalFilename_After_Delete() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "repeat.txt", MediaType.TEXT_PLAIN_VALUE, "first".getBytes(StandardCharsets.UTF_8));

        MvcResult firstUpload = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(firstUpload.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                .header("satoken", token))
            .andExpect(status().isOk());

        MockMultipartFile secondFile = new MockMultipartFile(
            "file", "repeat.txt", MediaType.TEXT_PLAIN_VALUE, "second".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
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
                    upload_status, is_upload_success
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            datasetId, userId, "unique.txt", "txt", 1L, "local-private",
            "success", true);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                    upload_status, is_upload_success
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            datasetId, userId, "unique.txt", "txt", 1L, "local-private",
            "success", true))
            .isInstanceOf(Exception.class);
    }

    @Test
    void Should_DownloadOriginalFileThroughInternalEndpoint_When_ServiceTokenMatches() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "private.txt", MediaType.TEXT_PLAIN_VALUE, "secret-content".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(get("/api/v1/internal/files/{fileId}/content", fileId)
                .header("Authorization", "Bearer test-service-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("secret-content"));
    }

    @Test
    void Should_RejectInternalDownload_When_ServiceTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/internal/files/{fileId}/content", 1L)
                .header("Authorization", "Bearer wrong"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void Should_CreateParseTaskAndSendMqMessage_When_UploadWithParseImmediately() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "parse.md", "text/markdown", "# parse".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .param("parseImmediately", "true")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadStatus").value("UPLOADING"))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Long fileId = data.get("id").asLong();
        String parseTaskId = jdbcTemplate.queryForObject(
            "SELECT latest_parse_task_id FROM document_parse_file WHERE document_original_file_id = ?",
            String.class, fileId);
        assertThat(parseTaskId).isNotBlank();
        assertThat(recordingMQSend.messages()).hasSize(1);
        assertThat(recordingMQSend.messages().get(0).getMQName()).isEqualTo("tolink.rag.parse_task");
        assertThat(recordingMQSend.messages().get(0).getMessage()).contains("\"original_file_id\":" + fileId)
            .contains("\"document_parse_file_id\":")
            .contains("\"trigger_mode\":\"upload_auto\"");
    }

    @Test
    void Should_CreateParseTaskAndSendMqMessage_When_UserStartsParseAfterUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "later.txt", MediaType.TEXT_PLAIN_VALUE, "parse later".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
            .file(file)
            .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        MvcResult result = mockMvc.perform(post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.frontendStatus").value("parsing"))
            .andReturn();

        String parseTaskId = jdbcTemplate.queryForObject(
            "SELECT latest_parse_task_id FROM document_parse_file WHERE document_original_file_id = ?",
            String.class, fileId);
        assertThat(parseTaskId).isNotBlank();
        assertThat(recordingMQSend.messages()).hasSize(1);
    }

    @Test
    void Should_BlockDuplicateParseSubmit_When_LatestPointerHasNoPythonLogYet() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "duplicate.txt", MediaType.TEXT_PLAIN_VALUE, "parse duplicate".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409));

        assertThat(recordingMQSend.messages()).hasSize(1);
    }

    @Test
    void Should_RollbackLatestPointer_When_ParseTaskMqSendFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "rollback.txt", MediaType.TEXT_PLAIN_VALUE, "parse rollback".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        Long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
        recordingMQSend.failNextSend();

        mockMvc.perform(post("/api/v1/files/{fileId}/parse", fileId)
                .header("satoken", token))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500));

        String parseTaskId = jdbcTemplate.queryForObject(
            "SELECT latest_parse_task_id FROM document_parse_file WHERE document_original_file_id = ?",
            String.class, fileId);
        assertThat(parseTaskId).isNull();
    }

    @Test
    void Should_QueryLatestParseResult_When_PythonLogExists() throws Exception {
        Long fileId = uploadPlainFile("result.txt", "parse result");
        Long parseFileId = jdbcTemplate.queryForObject(
            "SELECT id FROM document_parse_file WHERE document_original_file_id = ?", Long.class, fileId);
        jdbcTemplate.update("UPDATE document_parse_file SET latest_parse_task_id = ? WHERE id = ?",
            "task-result-1", parseFileId);
        jdbcTemplate.update("""
                INSERT INTO document_parsed_log (
                    task_id, document_original_file_id, document_parse_file_id, trigger_mode,
                    parsed_filename, parsed_bucket_name, parsed_object_key, parsed_file_url, parsed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            "task-result-1", fileId, parseFileId, "manual_retry",
            "result.md", "rag-md", "parsed/result.md", "internal://parsed/result.md");
        // 终态权威源为 pipeline_status（大写）；前端态由它推导。
        Long resultLogId = jdbcTemplate.queryForObject(
            "SELECT id FROM document_parsed_log WHERE task_id = 'task-result-1'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO document_parse_pipeline (
                    document_parsed_log_id, task_id, document_original_file_id, document_parse_file_id,
                    pipeline_status, finished_at
                ) VALUES (?, 'task-result-1', ?, ?, 'SUCCESS', CURRENT_TIMESTAMP)
                """, resultLogId, fileId, parseFileId);

        mockMvc.perform(get("/api/v1/datasets/{datasetId}/files/parse-results", datasetId)
                .param("fileIds", fileId.toString())
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].fileId").value(fileId))
            .andExpect(jsonPath("$.data[0].parseStatus").value("success"))
            .andExpect(jsonPath("$.data[0].frontendStatus").value("parse_success"))
            .andExpect(jsonPath("$.data[0].parsedFilename").value("result.md"));
    }

    private Long uploadPlainFile(String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", filename, MediaType.TEXT_PLAIN_VALUE, content.getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/datasets/{datasetId}/files", datasetId)
                .file(file)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private Long createUser(String username) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 1)
                """, username, passwordEncoder.encode("password123"), username, username + "@test.com");
        return jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    }

    private Long createDataset(Long ownerUserId, String name) {
        jdbcTemplate.update("""
                INSERT INTO dataset (user_id, name, description, status)
                VALUES (?, ?, 'recent document dataset', 'ACTIVE')
                """, ownerUserId, name);
        return jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ? AND name = ?",
            Long.class, ownerUserId, name);
    }

    private Long insertDocumentFile(Long ownerUserId, Long ownerDatasetId, String filename, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                    upload_status, is_upload_success, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            ownerDatasetId, ownerUserId, filename, "txt", 1L, "local-private",
            "success", true, createdAt, createdAt);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM document_original_file
                WHERE user_id = ? AND dataset_id = ? AND original_filename = ?
                """, Long.class, ownerUserId, ownerDatasetId, filename);
    }

    @TestConfiguration
    static class DocumentFileTestConfig {

        @Bean
        RecordingMQSend recordingMQSend() {
            return new RecordingMQSend();
        }

        @Bean("documentUploadExecutor")
        ControllableUploadExecutor documentUploadExecutor() {
            return new ControllableUploadExecutor();
        }
    }

    /**
     * 测试用 documentUploadExecutor：默认在新线程执行并 join 等待。
     *
     * <p>用新线程（而非 SyncTaskExecutor 内联）让异步任务脱离 upload() 事务 afterCommit 阶段仍绑定的
     * 事务资源，与生产环境线程池在独立线程执行一致；join 保证集成断言确定性。{@link #rejectNext()} 可模拟
     * 池满拒绝（在 afterCommit 抛 RejectedExecutionException），用于验证拒绝路径的终态回写仍能独立提交。</p>
     */
    static class ControllableUploadExecutor implements java.util.concurrent.Executor {

        private volatile boolean rejectNext = false;

        @Override
        public void execute(Runnable command) {
            if (rejectNext) {
                rejectNext = false;
                throw new java.util.concurrent.RejectedExecutionException("pool full (test)");
            }
            Thread thread = new Thread(command, "test-document-upload");
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void rejectNext() {
            this.rejectNext = true;
        }

        void reset() {
            this.rejectNext = false;
        }
    }

    static class RecordingMQSend implements MQSend {

        private final List<AbstractMQ> messages = new ArrayList<>();
        private boolean failNextSend;

        @Override
        public void send(AbstractMQ abstractMQ) {
            if (failNextSend) {
                failNextSend = false;
                throw new RuntimeException("mq send failed");
            }
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
            failNextSend = false;
        }

        void failNextSend() {
            failNextSend = true;
        }
    }
}
