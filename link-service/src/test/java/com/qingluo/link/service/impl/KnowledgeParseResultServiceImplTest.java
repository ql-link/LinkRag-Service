package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeParsedFile;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeParseResultServiceImplTest {

    @Mock
    private KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @Mock
    private KnowledgeParsedFileMapper knowledgeParsedFileMapper;

    @InjectMocks
    private KnowledgeParseResultServiceImpl knowledgeParseResultService;

    @Test
    @DisplayName("Should_InsertParsedFile_When_ParseResultIsSuccess")
    void Should_InsertParsedFile_When_ParseResultIsSuccess() {
        KnowledgeOriginalFile record = buildRecord("task-1", 101L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeParsedFileMapper.selectOne(any())).willReturn(null);

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

        ArgumentCaptor<KnowledgeParsedFile> parsedFileCaptor = ArgumentCaptor.forClass(KnowledgeParsedFile.class);
        verify(knowledgeParsedFileMapper).insert(parsedFileCaptor.capture());
        KnowledgeParsedFile parsedFile = parsedFileCaptor.getValue();
        assertThat(parsedFile.getDocumentOriginalFileId()).isEqualTo(101L);
        assertThat(parsedFile.getOriginalFilename()).isEqualTo("guide.md");
        assertThat(parsedFile.getParseStatus()).isEqualTo("success");
        assertThat(parsedFile.getIsParseSuccess()).isTrue();
        assertThat(parsedFile.getParsedBucketName()).isEqualTo("rag-parsed");
        assertThat(parsedFile.getParsedObjectKey()).isEqualTo("parsed/2026/04/21/101.md");
        assertThat(parsedFile.getParsedFileUrl()).isEqualTo("http://rag/101.md");
        assertThat(parsedFile.getParsedStoragePath()).isEqualTo("rag-parsed/parsed/2026/04/21/101.md");
        assertThat(parsedFile.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Should_InsertFailedParsedFile_When_ParseResultIsFailed")
    void Should_InsertFailedParsedFile_When_ParseResultIsFailed() {
        KnowledgeOriginalFile record = buildRecord("task-2", 102L);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeParsedFileMapper.selectOne(any())).willReturn(null);

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

        ArgumentCaptor<KnowledgeParsedFile> parsedFileCaptor = ArgumentCaptor.forClass(KnowledgeParsedFile.class);
        verify(knowledgeParsedFileMapper).insert(parsedFileCaptor.capture());
        KnowledgeParsedFile parsedFile = parsedFileCaptor.getValue();
        assertThat(parsedFile.getDocumentOriginalFileId()).isEqualTo(102L);
        assertThat(parsedFile.getOriginalFilename()).isEqualTo("guide.md");
        assertThat(parsedFile.getParseStatus()).isEqualTo("failed");
        assertThat(parsedFile.getIsParseSuccess()).isFalse();
        assertThat(parsedFile.getFailureReason()).isEqualTo("parse failed");
        assertThat(parsedFile.getParsedBucketName()).isNull();
        assertThat(parsedFile.getParsedObjectKey()).isNull();
        assertThat(parsedFile.getParsedFileUrl()).isNull();
    }

    @Test
    @DisplayName("Should_IgnoreFailedParseResult_When_RecordAlreadySucceeded")
    void Should_IgnoreFailedParseResult_When_RecordAlreadySucceeded() {
        KnowledgeOriginalFile record = buildRecord("task-3", 103L);
        KnowledgeParsedFile existingParsedFile = new KnowledgeParsedFile();
        existingParsedFile.setDocumentOriginalFileId(103L);
        existingParsedFile.setParseStatus("success");
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeParsedFileMapper.selectOne(any())).willReturn(existingParsedFile);

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
        verify(knowledgeParsedFileMapper, never()).insert(any(KnowledgeParsedFile.class));
        verify(knowledgeParsedFileMapper, never()).updateById(any(KnowledgeParsedFile.class));
    }

    @Test
    @DisplayName("Should_UpdateExistingParsedFileRecord_When_ParseResultRecordAlreadyExists")
    void Should_UpdateExistingParsedFileRecord_When_ParseResultRecordAlreadyExists() {
        KnowledgeOriginalFile record = buildRecord("task-5", 105L);
        KnowledgeParsedFile existingParsedFile = new KnowledgeParsedFile();
        existingParsedFile.setId(9001L);
        existingParsedFile.setDocumentOriginalFileId(105L);
        existingParsedFile.setParseStatus("failed");
        existingParsedFile.setIsParseSuccess(false);
        existingParsedFile.setFailureReason("old failure");
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeParsedFileMapper.selectOne(any())).willReturn(existingParsedFile);
        given(knowledgeParsedFileMapper.updateById(any(KnowledgeParsedFile.class))).willReturn(1);

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

        ArgumentCaptor<KnowledgeParsedFile> captor = ArgumentCaptor.forClass(KnowledgeParsedFile.class);
        verify(knowledgeParsedFileMapper).updateById(captor.capture());
        KnowledgeParsedFile updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(9001L);
        assertThat(updated.getDocumentOriginalFileId()).isEqualTo(105L);
        assertThat(updated.getParseStatus()).isEqualTo("success");
        assertThat(updated.getIsParseSuccess()).isTrue();
        assertThat(updated.getParsedStoragePath()).isEqualTo("rag-parsed/parsed/2026/04/21/105.md");
        assertThat(updated.getFailureReason()).isNull();
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
