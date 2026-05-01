package com.qingluo.link.service.impl.know;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import com.qingluo.link.service.KnowledgeParseSseService;
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

    @Mock
    private KnowledgeParseTaskMapper knowledgeParseTaskMapper;

    @Mock
    private KnowledgeParseSseService knowledgeParseSseService;

    @InjectMocks
    private KnowledgeParseResultServiceImpl knowledgeParseResultService;

    @Test
    @DisplayName("Should_PublishSseEvent_When_ParseResultIsSuccess")
    void Should_PublishSseEvent_When_ParseResultIsSuccess() {
        KnowledgeOriginalFile record = buildRecord(101L, 201L, 301L);
        KnowledgeParseTask task = buildTask(501L, "task-1", 101L, 201L, 301L);
        given(knowledgeParseTaskMapper.selectById(501L)).willReturn(task);
        given(knowledgeOriginalFileMapper.selectById(101L)).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            101L,
            501L,
            201L,
            301L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        ));

        verify(knowledgeParseSseService).publishResultEvent(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            101L,
            501L,
            201L,
            301L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        ));
    }

    @Test
    @DisplayName("Should_PublishSseEvent_When_ParseResultIsFailed")
    void Should_PublishSseEvent_When_ParseResultIsFailed() {
        KnowledgeOriginalFile record = buildRecord(102L, 202L, 302L);
        KnowledgeParseTask task = buildTask(502L, "task-2", 102L, 202L, 302L);
        given(knowledgeParseTaskMapper.selectById(502L)).willReturn(task);
        given(knowledgeOriginalFileMapper.selectById(102L)).willReturn(record);

        knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-2",
            102L,
            502L,
            202L,
            302L,
            "failed",
            "parse failed",
            "2026-04-28T10:00:08+08:00"
        ));

        verify(knowledgeParseSseService).publishResultEvent(new KnowledgeParseResultMQ.MsgPayload(
            "task-2",
            102L,
            502L,
            202L,
            302L,
            "failed",
            "parse failed",
            "2026-04-28T10:00:08+08:00"
        ));
    }

    @Test
    @DisplayName("Should_ThrowBusinessException_When_ParseLogMissing")
    void Should_ThrowBusinessException_When_ParseLogMissing() {
        given(knowledgeParseTaskMapper.selectById(503L)).willReturn(null);

        assertThatThrownBy(() -> knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-3",
            103L,
            503L,
            203L,
            303L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析任务不存在");
    }

    @Test
    @DisplayName("Should_ThrowBusinessException_When_OwnershipDoesNotMatch")
    void Should_ThrowBusinessException_When_OwnershipDoesNotMatch() {
        KnowledgeOriginalFile record = buildRecord(104L, 204L, 304L);
        KnowledgeParseTask task = buildTask(504L, "task-4", 104L, 204L, 304L);
        given(knowledgeParseTaskMapper.selectById(504L)).willReturn(task);
        given(knowledgeOriginalFileMapper.selectById(104L)).willReturn(record);

        assertThatThrownBy(() -> knowledgeParseResultService.handleParseResult(new KnowledgeParseResultMQ.MsgPayload(
            "task-4",
            104L,
            504L,
            999L,
            304L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessage("解析结果消息归属信息不匹配");
    }

    private KnowledgeOriginalFile buildRecord(Long documentId, Long datasetId, Long userId) {
        KnowledgeOriginalFile record = new KnowledgeOriginalFile();
        record.setId(documentId);
        record.setDatasetId(datasetId);
        record.setUserId(userId);
        record.setOriginalFilename("guide.md");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private KnowledgeParseTask buildTask(Long parseLogId, String taskId, Long fileId, Long datasetId, Long userId) {
        KnowledgeParseTask task = new KnowledgeParseTask();
        task.setId(parseLogId);
        task.setTaskId(taskId);
        task.setDocumentOriginalFileId(fileId);
        task.setDatasetId(datasetId);
        task.setUserId(userId);
        return task;
    }
}
