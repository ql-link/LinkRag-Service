package com.qingluo.link.service.impl.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsePipelineMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsePipeline;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentParseTaskServiceImplTest {

    @Mock private DatasetMapper datasetMapper;
    @Mock private DocumentOriginalFileMapper documentOriginalFileMapper;
    @Mock private DocumentParseFileMapper documentParseFileMapper;
    @Mock private DocumentParsedLogMapper documentParsedLogMapper;
    @Mock private DocumentParsePipelineMapper documentParsePipelineMapper;
    @Mock private org.springframework.beans.factory.ObjectProvider<MQSend> mqSendProvider;
    @Mock private MQSend mqSend;
    @InjectMocks private DocumentParseTaskServiceImpl service;

    @BeforeAll
    static void initMpTableInfo() {
        // LambdaUpdateWrapper.set 即时解析列名，需 MyBatis-Plus TableInfo 缓存（纯 Mockito 单测无 MyBatis 上下文）。
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), DocumentParseFile.class);
    }

    // ===== 入口分类 =====

    @Test
    void Should_FirstParse_When_NoHistory() {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile(null));
        given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

        service.submitManualParse(401L, 101L);

        JSONObject msg = capturedTask();
        assertThat(msg.getBooleanValue("is_retry")).isFalse();
        assertThat(msg.getString("previous_task_id")).isNull();
        assertThat(msg.getString("md_object_key")).startsWith("parsed/user-401/dataset-201");
        verify(documentParseFileMapper).update(any(), any());
    }

    @Test
    void Should_FirstParse_When_FailedBeforeMarkdownProduced() {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-old"));
        given(documentParsedLogMapper.selectOne(any())).willReturn(log("task-old", null, null));
        given(documentParsePipelineMapper.selectOne(any())).willReturn(pipeline("FAILED"));
        given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

        service.submitManualParse(401L, 101L);

        JSONObject msg = capturedTask();
        assertThat(msg.getBooleanValue("is_retry")).isFalse();
        // 未产出 Markdown → 新建路径，不复用
        assertThat(msg.getString("md_object_key")).startsWith("parsed/user-401/dataset-201");
    }

    @Test
    void Should_Retry_When_MarkdownProducedAndPipelineFailed() {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-old"));
        given(documentParsedLogMapper.selectOne(any())).willReturn(log("task-old", "rag-md", "parsed/old.md"));
        given(documentParsePipelineMapper.selectOne(any())).willReturn(pipeline("FAILED"));
        given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

        service.submitManualParse(401L, 101L);

        JSONObject msg = capturedTask();
        assertThat(msg.getBooleanValue("is_retry")).isTrue();
        assertThat(msg.getString("previous_task_id")).isEqualTo("task-old");
        assertThat(msg.getString("md_bucket")).isEqualTo("rag-md");
        assertThat(msg.getString("md_object_key")).isEqualTo("parsed/old.md");
        // 业务字段与原任务一致
        assertThat(msg.getLong("user_id")).isEqualTo(401L);
        assertThat(msg.getLong("dataset_id")).isEqualTo(201L);
        assertThat(msg.getString("trigger_mode")).isEqualTo("manual_retry");
    }

    @Test
    void Should_RetryPreviousPointToLatestFailedTask_When_MultipleRounds() {
        // 多轮重试：当前指针指向最近一次失败任务 task-2，previous 应为 task-2 而非更早的 task-1
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-2"));
        given(documentParsedLogMapper.selectOne(any())).willReturn(log("task-2", "rag-md", "parsed/k2.md"));
        given(documentParsePipelineMapper.selectOne(any())).willReturn(pipeline("FAILED"));
        given(mqSendProvider.getIfAvailable()).willReturn(mqSend);

        service.submitManualParse(401L, 101L);

        JSONObject msg = capturedTask();
        assertThat(msg.getString("previous_task_id")).isEqualTo("task-2");
        assertThat(msg.getString("md_object_key")).isEqualTo("parsed/k2.md");
    }

    @Test
    void Should_Reject_When_AlreadySucceeded() {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-ok"));
        given(documentParsedLogMapper.selectOne(any())).willReturn(log("task-ok", "rag-md", "parsed/ok.md"));
        given(documentParsePipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));

        assertThatThrownBy(() -> service.submitManualParse(401L, 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已解析成功");

        verify(documentParseFileMapper, never()).update(any(), any());
        verify(mqSend, never()).send(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PROCESSING"})
    void Should_Reject_When_PipelineRunning(String status) {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-run"));
        given(documentParsedLogMapper.selectOne(any())).willReturn(log("task-run", "rag-md", "parsed/x.md"));
        given(documentParsePipelineMapper.selectOne(any())).willReturn(pipeline(status));

        assertThatThrownBy(() -> service.submitManualParse(401L, 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("正在解析中");

        verify(documentParseFileMapper, never()).update(any(), any());
    }

    @Test
    void Should_Reject_When_PointerAheadOfPythonLog() {
        givenOwnedUploadedFile();
        given(documentParseFileMapper.selectOne(any())).willReturn(parseFile("task-new"));
        // 指针已设但 Python 日志未到 → 运行中
        given(documentParsedLogMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.submitManualParse(401L, 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("正在解析中");

        verify(documentParseFileMapper, never()).update(any(), any());
    }

    // ===== helpers =====

    private void givenOwnedUploadedFile() {
        given(documentOriginalFileMapper.selectOne(any())).willReturn(ownedFile());
    }

    private JSONObject capturedTask() {
        ArgumentCaptor<AbstractMQ> captor = ArgumentCaptor.forClass(AbstractMQ.class);
        verify(mqSend).send(captor.capture());
        return JSON.parseObject(captor.getValue().getMessage());
    }

    private DocumentOriginalFile ownedFile() {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(101L);
        file.setUserId(401L);
        file.setDatasetId(201L);
        file.setOriginalFilename("a.pdf");
        file.setFileSuffix("pdf");
        file.setBucketName("rag-raw");
        file.setObjectKey("raw/a.pdf");
        file.setUploadStatus("success");
        file.setIsUploadSuccess(true);
        return file;
    }

    private DocumentParseFile parseFile(String latestTaskId) {
        DocumentParseFile parseFile = new DocumentParseFile();
        parseFile.setId(301L);
        parseFile.setDocumentOriginalFileId(101L);
        parseFile.setDatasetId(201L);
        parseFile.setUserId(401L);
        parseFile.setLatestParseTaskId(latestTaskId);
        return parseFile;
    }

    private DocumentParsedLog log(String taskId, String parsedBucket, String parsedObjectKey) {
        DocumentParsedLog log = new DocumentParsedLog();
        log.setTaskId(taskId);
        log.setDocumentOriginalFileId(101L);
        log.setDocumentParseFileId(301L);
        log.setParsedBucketName(parsedBucket);
        log.setParsedObjectKey(parsedObjectKey);
        return log;
    }

    private DocumentParsePipeline pipeline(String status) {
        DocumentParsePipeline pipeline = new DocumentParsePipeline();
        pipeline.setPipelineStatus(status);
        return pipeline;
    }
}
