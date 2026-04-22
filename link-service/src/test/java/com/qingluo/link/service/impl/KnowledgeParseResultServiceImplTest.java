package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
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
    @DisplayName("Should_UpdateParsedFields_When_ParseResultIsSuccess")
    void Should_UpdateParsedFields_When_ParseResultIsSuccess() {
        KnowledgeOriginalFile record = buildRecord("task-1", 101L, "pending", false);
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeOriginalFileMapper.updateById(any(KnowledgeOriginalFile.class))).willReturn(1);

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

        ArgumentCaptor<KnowledgeOriginalFile> captor = ArgumentCaptor.forClass(KnowledgeOriginalFile.class);
        verify(knowledgeOriginalFileMapper).updateById(captor.capture());
        KnowledgeOriginalFile updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(101L);
        assertThat(updated.getParseStatus()).isEqualTo("success");
        assertThat(updated.getIsParseSuccess()).isTrue();
        assertThat(updated.getParsedBucketName()).isEqualTo("rag-parsed");
        assertThat(updated.getParsedObjectKey()).isEqualTo("parsed/2026/04/21/101.md");
        assertThat(updated.getParsedFileUrl()).isEqualTo("http://rag/101.md");
        assertThat(updated.getParseFailureReason()).isNull();
        assertThat(updated.getParsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should_UpdateFailureReason_When_ParseResultIsFailed")
    void Should_UpdateFailureReason_When_ParseResultIsFailed() {
        KnowledgeOriginalFile record = buildRecord("task-2", 102L, "pending", false);
        record.setParsedBucketName("old-bucket");
        record.setParsedObjectKey("old-key");
        record.setParsedFileUrl("old-url");
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(record);
        given(knowledgeOriginalFileMapper.updateById(any(KnowledgeOriginalFile.class))).willReturn(1);

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

        ArgumentCaptor<KnowledgeOriginalFile> captor = ArgumentCaptor.forClass(KnowledgeOriginalFile.class);
        verify(knowledgeOriginalFileMapper).updateById(captor.capture());
        KnowledgeOriginalFile updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(102L);
        assertThat(updated.getParseStatus()).isEqualTo("failed");
        assertThat(updated.getIsParseSuccess()).isFalse();
        assertThat(updated.getParseFailureReason()).isEqualTo("parse failed");
        assertThat(updated.getParsedBucketName()).isNull();
        assertThat(updated.getParsedObjectKey()).isNull();
        assertThat(updated.getParsedFileUrl()).isNull();
    }

    @Test
    @DisplayName("Should_IgnoreFailedParseResult_When_RecordAlreadySucceeded")
    void Should_IgnoreFailedParseResult_When_RecordAlreadySucceeded() {
        KnowledgeOriginalFile record = buildRecord("task-3", 103L, "success", true);
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
    @DisplayName("Should_ThrowBusinessException_When_DocumentIdDoesNotMatch")
    void Should_ThrowBusinessException_When_DocumentIdDoesNotMatch() {
        KnowledgeOriginalFile record = buildRecord("task-4", 104L, "pending", false);
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

    private KnowledgeOriginalFile buildRecord(String taskId, Long documentId, String parseStatus, boolean parseSuccess) {
        KnowledgeOriginalFile record = new KnowledgeOriginalFile();
        record.setId(documentId);
        record.setDatasetId(201L);
        record.setParseTaskId(taskId);
        record.setParseStatus(parseStatus);
        record.setIsParseSuccess(parseSuccess);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
