package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class KnowledgeParseTaskServiceImplTest {

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

    @Test
    @DisplayName("Should_NotRedispatchCreatedTask_When_LastDispatchSucceeded")
    void Should_NotRedispatchCreatedTask_When_LastDispatchSucceeded() {
        KnowledgeFileProperties properties = new KnowledgeFileProperties();
        properties.setParseDispatchMaxRetryCount(5);
        properties.setParseDispatchRetryIntervalSeconds(30);
        KnowledgeParseTaskServiceImpl service = new KnowledgeParseTaskServiceImpl(
            datasetMapper,
            knowledgeOriginalFileMapper,
            knowledgeParseTaskMapper,
            knowledgeParsedFileMapper,
            mqSendProvider,
            properties);
        KnowledgeParseTask task = new KnowledgeParseTask();
        task.setId(1L);
        task.setTaskId("task-1");
        task.setDocumentOriginalFileId(10001L);
        task.setTaskStatus(KnowledgeParseTaskServiceImpl.TASK_CREATED);
        task.setDispatchRetryCount(0);
        task.setLastDispatchedAt(LocalDateTime.now().minusMinutes(5));
        task.setLastDispatchError(null);
        given(knowledgeParseTaskMapper.selectList(any())).willReturn(List.of(task));

        int affected = service.compensateCreatedTasks();

        assertThat(affected).isZero();
        verify(mqSendProvider, never()).getIfAvailable();
        verify(mqSend, never()).send(any());
    }
}
