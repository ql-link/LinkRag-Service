package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsePipelineMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsePipeline;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.service.DocumentParseSseService;
import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.exception.ParseResultPendingException;
import com.qingluo.link.service.impl.document.DocumentParseResultServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentParseResultServiceImplTest {

    @Mock private DocumentOriginalFileMapper originalFileMapper;
    @Mock private DocumentParseFileMapper parseFileMapper;
    @Mock private DocumentParsedLogMapper parsedLogMapper;
    @Mock private DocumentParsePipelineMapper pipelineMapper;
    @Mock private DocumentParseSseService sseService;
    @InjectMocks private DocumentParseResultServiceImpl service;

    // ===== 当前任务过滤 =====

    @Test
    void Should_PublishSse_When_MessageIsCurrentTask() {
        DocumentParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, "task-1"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService).publishResultEvent(payload);
        assertNoBusinessWrite();
    }

    @Test
    void Should_SkipSse_When_MessageIsStaleNonCurrentTask() {
        DocumentParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));
        // 当前任务已是 task-new，消息属于旧任务 task-1
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, "task-new"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_FailOpenAndPublish_When_LatestTaskPointerMissing() {
        DocumentParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));
        // latestParseTaskId 为空（历史数据）
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, null));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService).publishResultEvent(payload);
        assertNoBusinessWrite();
    }

    // ===== 失败分类 =====

    @Test
    void Should_ThrowPending_When_LogNotYetPersisted() {
        given(parsedLogMapper.selectById(201L)).willReturn(null);

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(ParseResultPendingException.class);

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_ThrowPending_When_PipelineNotYetPersisted() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        // 流水线行暂缺（跨库可见性/主从延迟）→ 可重试
        given(pipelineMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(ParseResultPendingException.class);

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_ThrowNonRetryable_When_TaskIdDoesNotMatchPythonLog() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "another-task", 101L, 301L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息中的任务标识不匹配");

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_ThrowNonRetryable_When_MessageStatusDoesNotMatchPipeline() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        // 消息 task_status=success，但库侧 pipeline_status=FAILED → 不一致
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("FAILED"));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息状态与已持久化流水线终态不匹配");

        verify(sseService, never()).publishResultEvent(any());
    }

    @Test
    void Should_ThrowNonRetryable_When_OwnershipDoesNotMatchParseFile() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 999L, 401L, "task-1"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息归属信息不匹配");
    }

    // ===== 幂等 / 不变量 =====

    @Test
    void Should_BeIdempotent_When_ProcessedMultipleTimes() {
        DocumentParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(pipelineMapper.selectOne(any())).willReturn(pipeline("SUCCESS"));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, "task-1"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);
        service.handleParseResult(payload);

        // 重试只重复转发 SSE，无业务写副作用
        verify(sseService, times(2)).publishResultEvent(payload);
        assertNoBusinessWrite();
    }

    private void assertNoBusinessWrite() {
        verify(originalFileMapper, never()).updateById(any());
        verify(parseFileMapper, never()).updateById(any());
        verify(parsedLogMapper, never()).updateById(any());
        verify(parsedLogMapper, never()).insert(any());
        verify(pipelineMapper, never()).updateById(any());
        verify(pipelineMapper, never()).insert(any());
    }

    private DocumentParseResultMQ.MsgPayload payload(Long fileId, Long logId, Long datasetId, Long userId) {
        return new DocumentParseResultMQ.MsgPayload(
            "task-1", fileId, logId, datasetId, userId, "success", null, "2026-05-27T10:00:08+08:00");
    }

    private DocumentParsedLog log(Long id, String taskId, Long fileId, Long parseFileId) {
        DocumentParsedLog log = new DocumentParsedLog();
        log.setId(id);
        log.setTaskId(taskId);
        log.setDocumentOriginalFileId(fileId);
        log.setDocumentParseFileId(parseFileId);
        return log;
    }

    private DocumentParsePipeline pipeline(String status) {
        DocumentParsePipeline pipeline = new DocumentParsePipeline();
        pipeline.setPipelineStatus(status);
        return pipeline;
    }

    private DocumentParseFile parseFile(Long id, Long fileId, Long datasetId, Long userId, String latestTaskId) {
        DocumentParseFile file = new DocumentParseFile();
        file.setId(id);
        file.setDocumentOriginalFileId(fileId);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
        file.setLatestParseTaskId(latestTaskId);
        return file;
    }

    private DocumentOriginalFile original(Long id, Long datasetId, Long userId) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(id);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
        return file;
    }
}
