package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeParseResultServiceImplTest {

    @Mock
    private KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @InjectMocks
    private KnowledgeParseResultServiceImpl knowledgeParseResultService;

    @Test
    @DisplayName("Should_OnlyLogCompatibilityMessage_When_ParseResultIsSuccess")
    void Should_OnlyLogCompatibilityMessage_When_ParseResultIsSuccess() {
        KnowledgeOriginalFile record = buildRecord("task-1", 101L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            "101",
            true,
            "success",
            "rag-parsed",
            "parsed/2026/04/21/101.md",
            "http://rag/101.md",
            null,
            1200L
        ));

        verify(knowledgeOriginalFileMapper, never()).updateById(any(KnowledgeOriginalFile.class));
    }

    @Test
    @DisplayName("Should_OnlyLogCompatibilityMessage_When_ParseResultIsFailed")
    void Should_OnlyLogCompatibilityMessage_When_ParseResultIsFailed() {
        KnowledgeOriginalFile record = buildRecord("task-2", 102L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-2",
            "102",
            false,
            "failed",
            null,
            null,
            null,
            "parse failed",
            800L
        ));

        verify(knowledgeOriginalFileMapper, never()).updateById(any(KnowledgeOriginalFile.class));
    }

    @Test
    @DisplayName("Should_NotWriteParsedFile_When_ParseResultIsCompatibilityMessage")
    void Should_NotWriteParsedFile_When_ParseResultIsCompatibilityMessage() {
        KnowledgeOriginalFile record = buildRecord("task-3", 103L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-3",
            "103",
            false,
            "failed",
            null,
            null,
            null,
            "late failure",
            900L
        ));

        verify(knowledgeOriginalFileMapper, never()).updateById(any(KnowledgeOriginalFile.class));
    }

    @Test
    @DisplayName("Should_NotUpdateParsedFile_When_ParseResultRecordAlreadyExists")
    void Should_NotUpdateParsedFile_When_ParseResultRecordAlreadyExists() {
        KnowledgeOriginalFile record = buildRecord("task-5", 105L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-5",
            "105",
            true,
            "success",
            "rag-parsed",
            "parsed/2026/04/21/105.md",
            "http://rag/105.md",
            null,
            321L
        ));

        verify(knowledgeOriginalFileMapper, never()).updateById(any(KnowledgeOriginalFile.class));
    }

    @Test
    @DisplayName("Should_ThrowBusinessException_When_DocumentIdDoesNotMatch")
    void Should_ThrowBusinessException_When_DocumentIdDoesNotMatch() {
        KnowledgeOriginalFile record = buildRecord("task-4", 104L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);

        assertThatThrownBy(() -> knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-4",
            "999",
            true,
            "success",
            "rag-parsed",
            "parsed/key",
            "http://rag/key",
            null,
            100L
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析结果消息中的文档标识不匹配");
    }

    private KnowledgeOriginalFile buildRecord(String taskId, Long documentId) {
        KnowledgeOriginalFile record = new KnowledgeOriginalFile();
        record.setId(documentId);
        record.setDatasetId(201L);
        record.setUserId(301L);
        record.setOriginalFilename("guide.md");
        record.setParseTaskId(taskId);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
