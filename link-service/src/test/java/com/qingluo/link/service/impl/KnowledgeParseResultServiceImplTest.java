package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedLogMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedLog;
import com.qingluo.link.service.KnowledgeParseSseService;
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

    @Test
    void Should_PublishSseWithoutWritingOriginalFile_When_PythonResultMatches() {
        KnowledgeParseResultMQ.MsgPayload payload = payload(101L, 201L, 301L, 401L);
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 301L, 401L));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        service.handleParseResult(payload);

        verify(sseService).publishResultEvent(payload);
        verify(originalFileMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void Should_RejectResult_When_TaskIdDoesNotMatchPythonLog() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "another-task", 101L, 301L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析结果消息中的任务标识不匹配");
    }

    @Test
    void Should_RejectResult_When_MessageStatusDoesNotMatchPythonLog() {
        KnowledgeParsedLog log = log(201L, "task-1", 101L, 301L);
        log.setTaskStatus("failed");
        given(parsedLogMapper.selectById(201L)).willReturn(log);

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析结果消息状态与已持久化状态不匹配");
    }

    @Test
    void Should_RejectResult_When_OwnershipDoesNotMatchParseFile() {
        given(parsedLogMapper.selectById(201L)).willReturn(log(201L, "task-1", 101L, 301L));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 101L, 999L, 401L));
        given(originalFileMapper.selectById(101L)).willReturn(original(101L, 301L, 401L));

        assertThatThrownBy(() -> service.handleParseResult(payload(101L, 201L, 301L, 401L)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析结果消息归属信息不匹配");
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

    private KnowledgeParseFile parseFile(Long id, Long fileId, Long datasetId, Long userId) {
        KnowledgeParseFile file = new KnowledgeParseFile();
        file.setId(id);
        file.setDocumentOriginalFileId(fileId);
        file.setDatasetId(datasetId);
        file.setUserId(userId);
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
