package com.qingluo.link.service.impl.know;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import com.qingluo.link.model.dto.entity.KnowledgeParsedFile;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Phase 2 parse task service tests.
 *
 * <p>Key behaviors to verify:
 * <ul>
 *   <li>MQ message includes parsed_file_id</li>
 *   <li>Same file in-progress task prevents duplicate submission</li>
 *   <li>Frontend status mapping for all states</li>
 *   <li>No duplicate creation of parsed_file records on re-parse</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeParseTaskServiceImplPhase2Test {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    @Mock
    private KnowledgeParseTaskMapper knowledgeParseTaskMapper;
    @Mock
    private KnowledgeParsedFileMapper knowledgeParsedFileMapper;
    @Mock
    private ObjectProvider<MQSend> mqSendProvider;
    @Mock
    private MQSend mqSend;

    private KnowledgeFileProperties properties;
    private KnowledgeParseTaskServiceImpl service;

    private static final String UPLOAD_SUCCESS = "success";

    @BeforeEach
    void setUp() {
        properties = new KnowledgeFileProperties();
        properties.setParseDispatchMaxRetryCount(5);
        properties.setParseDispatchRetryIntervalSeconds(30);
        service = new KnowledgeParseTaskServiceImpl(
            datasetMapper,
            knowledgeOriginalFileMapper,
            knowledgeParseTaskMapper,
            knowledgeParsedFileMapper,
            mqSendProvider,
            properties);
    }

    @Nested
    @DisplayName("submitManualParse tests")
    class SubmitManualParseTests {

        @Test
        @DisplayName("Should_success_when_normal_parse_submission")
        void Should_success_when_normal_parse_submission() {
            // Given: uploaded file exists with parsed file record, no running task
            Long userId = 10000L;
            Long fileId = 10001L;
            Long datasetId = 20001L;
            Long parsedFileId = 30001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(parsedFileId, fileId, datasetId, userId);

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(null); // no running task
            given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

            // When
            FileParseSubmitDTO result = service.submitManualParse(userId, fileId);

            // Then: Java 已受理并完成 MQ 投递后，前端立即进入解析中
            assertThat(result).isNotNull();
            assertThat(result.getFileId()).isEqualTo(fileId);
            assertThat(result.getFrontendStatus()).isEqualTo("parsing");
        }

        @Test
        @DisplayName("Should_reject_when_same_file_already_has_running_task")
        void Should_reject_when_same_file_already_has_running_task() {
            // Given: file has a running task (created or processing)
            Long userId = 10000L;
            Long fileId = 10001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, 20001L, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, fileId, 20001L, userId);
            KnowledgeParseTask runningTask = new KnowledgeParseTask();
            runningTask.setId(1L);
            runningTask.setTaskId("existing-task-id");
            runningTask.setDocumentOriginalFileId(fileId);
            runningTask.setTaskStatus("created");

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(runningTask);

            // When/Then: throws BusinessException
            assertThatThrownBy(() -> service.submitManualParse(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件正在解析中");
        }

        @Test
        @DisplayName("Should_reject_when_latest_task_pointer_exists_but_python_has_not_created_log")
        void Should_reject_when_latest_task_pointer_exists_but_python_has_not_created_log() {
            Long userId = 10000L;
            Long fileId = 10001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, 20001L, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, fileId, 20001L, userId);
            parsedFile.setLatestParseTaskId("pending-python-log");

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(null);

            assertThatThrownBy(() -> service.submitManualParse(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件正在解析中");
        }

        @Test
        @DisplayName("Should_reject_when_file_not_uploaded")
        void Should_reject_when_file_not_uploaded() {
            Long userId = 10000L;
            Long fileId = 10001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, 20001L, userId);
            file.setUploadStatus("failed");

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);

            assertThatThrownBy(() -> service.submitManualParse(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未上传成功");
        }

        @Test
        @DisplayName("Should_reject_when_parsed_file_not_exists")
        void Should_reject_when_parsed_file_not_exists() {
            Long userId = 10000L;
            Long fileId = 10001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, 20001L, userId);

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(null);

            assertThatThrownBy(() -> service.submitManualParse(userId, fileId))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Should_throw_submit_failed_when_mq_dispatch_throws")
        void Should_throw_submit_failed_when_mq_dispatch_throws() {
            Long userId = 10000L;
            Long fileId = 10001L;
            Long datasetId = 20001L;
            Long parsedFileId = 30001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(parsedFileId, fileId, datasetId, userId);

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(null);
            given(mqSendProvider.getIfAvailable()).willReturn(mqSend);
            org.mockito.Mockito.doThrow(new IllegalStateException("mq down")).when(mqSend).send(any());

            assertThatThrownBy(() -> service.submitManualParse(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("解析提交失败");
        }
    }

    @Nested
    @DisplayName("submitAutoParseAfterUpload tests")
    class SubmitAutoParseAfterUploadTests {

        @Test
        @DisplayName("Should_dispatch_auto_parse_when_parse_immediately_enabled")
        void Should_dispatch_auto_parse_when_parse_immediately_enabled() {
            Long userId = 10000L;
            Long fileId = 10001L;
            Long datasetId = 20001L;
            Long parsedFileId = 30001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(parsedFileId, fileId, datasetId, userId);

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(null);
            given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

            service.submitAutoParseAfterUpload(userId, file);

            verify(knowledgeParseTaskMapper, never()).insert(any(KnowledgeParseTask.class));
            verify(knowledgeParsedFileMapper).update(any(), any());
            verify(mqSend).send(any());
        }

        @Test
        @DisplayName("Should_skip_auto_parse_when_running_task_exists")
        void Should_skip_auto_parse_when_running_task_exists() {
            Long userId = 10000L;
            Long fileId = 10001L;
            Long datasetId = 20001L;
            Long parsedFileId = 30001L;

            KnowledgeOriginalFile file = createUploadedFile(fileId, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(parsedFileId, fileId, datasetId, userId);
            KnowledgeParseTask runningTask = new KnowledgeParseTask();
            runningTask.setTaskId("running-task");
            runningTask.setDocumentOriginalFileId(fileId);
            runningTask.setTaskStatus("processing");

            given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
            given(knowledgeParsedFileMapper.selectOne(any())).willReturn(parsedFile);
            given(knowledgeParseTaskMapper.selectOne(any())).willReturn(runningTask);

            service.submitAutoParseAfterUpload(userId, file);

            verify(knowledgeParseTaskMapper, never()).insert(any(KnowledgeParseTask.class));
            verify(knowledgeParsedFileMapper, never()).update(any(), any());
            verify(mqSend, never()).send(any());
        }
    }

    @Nested
    @DisplayName("listParseResults frontend status mapping tests")
    class ListParseResultsTests {

        @Test
        @DisplayName("Should_return_parsing_when_no_task_but_has_latest_parse_task_id")
        void Should_return_parsing_when_no_task_but_has_latest_parse_task_id() {
            Long userId = 10000L;
            Long datasetId = 20001L;
            List<Long> fileIds = List.of(10001L);

            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setUserId(userId);

            KnowledgeOriginalFile file = createUploadedFile(10001L, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, 10001L, datasetId, userId);
            parsedFile.setLatestParseTaskId("task-123");

            KnowledgeParseTask task = new KnowledgeParseTask();
            task.setTaskId("task-123");
            task.setDocumentOriginalFileId(10001L);
            task.setTaskStatus("created");

            given(datasetMapper.selectOne(any())).willReturn(dataset);
            given(knowledgeOriginalFileMapper.selectList(any())).willReturn(List.of(file));
            given(knowledgeParsedFileMapper.selectList(any())).willReturn(List.of(parsedFile));
            given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(task));

            List<FileParseResultDTO> results = service.listParseResults(userId, datasetId, fileIds);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFrontendStatus()).isEqualTo("parsing");
        }

        @Test
        @DisplayName("Should_return_parsing_when_task_is_processing")
        void Should_return_parsing_when_task_is_processing() {
            Long userId = 10000L;
            Long datasetId = 20001L;
            List<Long> fileIds = List.of(10001L);

            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setUserId(userId);

            KnowledgeOriginalFile file = createUploadedFile(10001L, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, 10001L, datasetId, userId);
            parsedFile.setLatestParseTaskId("task-123");

            KnowledgeParseTask task = new KnowledgeParseTask();
            task.setTaskId("task-123");
            task.setDocumentOriginalFileId(10001L);
            task.setTaskStatus("processing");

            given(datasetMapper.selectOne(any())).willReturn(dataset);
            given(knowledgeOriginalFileMapper.selectList(any())).willReturn(List.of(file));
            given(knowledgeParsedFileMapper.selectList(any())).willReturn(List.of(parsedFile));
            given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(task));

            List<FileParseResultDTO> results = service.listParseResults(userId, datasetId, fileIds);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFrontendStatus()).isEqualTo("parsing");
        }

        @Test
        @DisplayName("Should_return_parse_success_when_task_is_success")
        void Should_return_parse_success_when_task_is_success() {
            Long userId = 10000L;
            Long datasetId = 20001L;
            List<Long> fileIds = List.of(10001L);

            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setUserId(userId);

            KnowledgeOriginalFile file = createUploadedFile(10001L, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, 10001L, datasetId, userId);
            parsedFile.setLatestParseTaskId("task-123");

            KnowledgeParseTask task = new KnowledgeParseTask();
            task.setTaskId("task-123");
            task.setDocumentOriginalFileId(10001L);
            task.setTaskStatus("success");

            given(datasetMapper.selectOne(any())).willReturn(dataset);
            given(knowledgeOriginalFileMapper.selectList(any())).willReturn(List.of(file));
            given(knowledgeParsedFileMapper.selectList(any())).willReturn(List.of(parsedFile));
            given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(task));

            List<FileParseResultDTO> results = service.listParseResults(userId, datasetId, fileIds);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFrontendStatus()).isEqualTo("parse_success");
        }

        @Test
        @DisplayName("Should_return_parse_failed_when_task_is_failed")
        void Should_return_parse_failed_when_task_is_failed() {
            Long userId = 10000L;
            Long datasetId = 20001L;
            List<Long> fileIds = List.of(10001L);

            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setUserId(userId);

            KnowledgeOriginalFile file = createUploadedFile(10001L, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, 10001L, datasetId, userId);
            parsedFile.setLatestParseTaskId("task-123");

            KnowledgeParseTask task = new KnowledgeParseTask();
            task.setTaskId("task-123");
            task.setDocumentOriginalFileId(10001L);
            task.setTaskStatus("failed");
            task.setFailureReason("PARSE_TIMEOUT");

            given(datasetMapper.selectOne(any())).willReturn(dataset);
            given(knowledgeOriginalFileMapper.selectList(any())).willReturn(List.of(file));
            given(knowledgeParsedFileMapper.selectList(any())).willReturn(List.of(parsedFile));
            given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(task));

            List<FileParseResultDTO> results = service.listParseResults(userId, datasetId, fileIds);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFrontendStatus()).isEqualTo("parse_failed");
            assertThat(results.get(0).getFailureReason()).isEqualTo("PARSE_TIMEOUT");
        }

        @Test
        @DisplayName("Should_use_latest_parse_task_pointer_instead_of_history_order")
        void Should_use_latest_parse_task_pointer_instead_of_history_order() {
            Long userId = 10000L;
            Long datasetId = 20001L;
            List<Long> fileIds = List.of(10001L);

            Dataset dataset = new Dataset();
            dataset.setId(datasetId);
            dataset.setUserId(userId);

            KnowledgeOriginalFile file = createUploadedFile(10001L, datasetId, userId);
            KnowledgeParsedFile parsedFile = createParsedFile(30001L, 10001L, datasetId, userId);
            parsedFile.setLatestParseTaskId("failed-task");

            KnowledgeParseTask successTask = new KnowledgeParseTask();
            successTask.setTaskId("success-task");
            successTask.setDocumentOriginalFileId(10001L);
            successTask.setTaskStatus("success");

            KnowledgeParseTask failedTask = new KnowledgeParseTask();
            failedTask.setTaskId("failed-task");
            failedTask.setDocumentOriginalFileId(10001L);
            failedTask.setTaskStatus("failed");
            failedTask.setFailureReason("LATEST_FAILED");

            given(datasetMapper.selectOne(any())).willReturn(dataset);
            given(knowledgeOriginalFileMapper.selectList(any())).willReturn(List.of(file));
            given(knowledgeParsedFileMapper.selectList(any())).willReturn(List.of(parsedFile));
            given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(successTask, failedTask));

            List<FileParseResultDTO> results = service.listParseResults(userId, datasetId, fileIds);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getParseStatus()).isEqualTo("failed");
            assertThat(results.get(0).getFrontendStatus()).isEqualTo("parse_failed");
            assertThat(results.get(0).getFailureReason()).isEqualTo("LATEST_FAILED");
        }
    }

    // Helper methods
    private KnowledgeOriginalFile createUploadedFile(Long id, Long datasetId, Long userId) {
        KnowledgeOriginalFile file = new KnowledgeOriginalFile();
        file.setId(id);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
        file.setOriginalFilename("test.pdf");
        file.setFileSuffix("pdf");
        file.setUploadStatus(UPLOAD_SUCCESS);
        file.setIsUploadSuccess(true);
        return file;
    }

    private KnowledgeParsedFile createParsedFile(Long id, Long originalFileId, Long datasetId, Long userId) {
        KnowledgeParsedFile parsedFile = new KnowledgeParsedFile();
        parsedFile.setId(id);
        parsedFile.setDocumentOriginalFileId(originalFileId);
        parsedFile.setDatasetId(datasetId);
        parsedFile.setUserId(userId);
        parsedFile.setParseCount(0);
        return parsedFile;
    }
}
