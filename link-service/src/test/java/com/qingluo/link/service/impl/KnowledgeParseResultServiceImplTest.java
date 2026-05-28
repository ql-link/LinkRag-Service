package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedLogMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedLog;
import com.qingluo.link.service.KnowledgeParseSseService;
import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.exception.ParseResultPendingException;
import com.qingluo.link.service.impl.know.KnowledgeParseResultServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeParseResultServiceImplTest {

    @Mock private KnowledgeOriginalFileMapper originalFileMapper;
    @Mock private KnowledgeParseFileMapper parseFileMapper;
    @Mock private KnowledgeParsedLogMapper parsedLogMapper;
    @Mock private KnowledgeParseSseService sseService;
    @InjectMocks private KnowledgeParseResultServiceImpl service;

    // ===== 当前任务过滤 =====

    @Test
    void Should_PublishSse_When_MessageIsCurrentTask() {
        KnowledgeParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, "task-1"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService).publishResultEvent(payload);
        assertNoBusinessWrite();
    }

    @Test
    void Should_SkipSse_When_MessageIsStaleNonCurrentTask() {
        KnowledgeParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        // 当前任务已是 task-new，消息属于旧任务 task-1
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L, "task-new"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_FailOpenAndPublish_When_LatestTaskPointerMissing() {
        KnowledgeParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
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
    void Should_ThrowNonRetryable_When_TaskIdDoesNotMatchPythonLog() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "another-task", 101L, 301L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息中的任务标识不匹配");

        verify(sseService, never()).publishResultEvent(any());
        assertNoBusinessWrite();
    }

    @Test
    void Should_ThrowNonRetryable_When_MessageStatusDoesNotMatchPythonLog() {
        KnowledgeParsedLog log = log(201L, "task-1", 101L, 301L);
        log.setTaskStatus("failed");
        given(parsedLogMapper.selectById(201L)).willReturn(log);

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息状态与已持久化状态不匹配");
    }

    @Test
    void Should_ThrowNonRetryable_When_OwnershipDoesNotMatchParseFile() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 999L, 401L, "task-1"));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(NonRetryableParseResultException.class)
            .hasMessage("解析结果消息归属信息不匹配");
    }

    // ===== 幂等 / 不变量 =====

    @Test
    void Should_BeIdempotent_When_ProcessedMultipleTimes() {
        KnowledgeParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
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
    }

    private KnowledgeParseResultMQ.MsgPayload payload(Long fileId, Long logId, Long datasetId, Long userId) {
        return new KnowledgeParseResultMQ.MsgPayload(
            "task-1", fileId, logId, datasetId, userId, "success", null, "2026-05-27T10:00:08+08:00");
    }

    private KnowledgeParsedLog log(Long id, String taskId, Long fileId, Long parseFileId) {
        KnowledgeParsedLog log = new KnowledgeParsedLog();
        log.setId(id);
        log.setTaskId(taskId);
        log.setDocumentOriginalFileId(fileId);
        log.setDocumentParseFileId(parseFileId);
        log.setTaskStatus("success");
        return log;
    }

    private KnowledgeParseFile parseFile(Long id, Long fileId, Long datasetId, Long userId, String latestTaskId) {
        KnowledgeParseFile file = new KnowledgeParseFile();
        file.setId(id);
        file.setDocumentOriginalFileId(fileId);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
        file.setLatestParseTaskId(latestTaskId);
        return file;
    }

    private KnowledgeOriginalFile original(Long id, Long datasetId, Long userId) {
        KnowledgeOriginalFile file = new KnowledgeOriginalFile();
        file.setId(id);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
        return file;
    }
}
