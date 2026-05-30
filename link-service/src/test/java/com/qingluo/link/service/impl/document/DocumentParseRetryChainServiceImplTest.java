package com.qingluo.link.service.impl.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentParseRetryChainServiceImplTest {

    @Mock private DocumentParsedLogMapper parsedLogMapper;
    private DocumentParseRetryChainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseRetryChainServiceImpl(parsedLogMapper);
        // @Value 默认在单测不注入，显式设置回溯深度上限。
        ReflectionTestUtils.setField(service, "maxDepth", 32);
    }

    @Test
    void Should_TraceFullChain_When_RetryOfLinksToOrigin() {
        // T3 -> T2 -> T1(origin, retry_of=null)
        given(parsedLogMapper.selectOne(any())).willReturn(
            log("task-3", "task-2"), log("task-2", "task-1"), log("task-1", null));

        assertThat(service.traceChain("task-3")).containsExactly("task-3", "task-2", "task-1");
    }

    @Test
    void Should_ReturnSingleNode_When_FirstParseHasNoRetryOf() {
        given(parsedLogMapper.selectOne(any())).willReturn(log("task-1", null));

        assertThat(service.traceChain("task-1")).containsExactly("task-1");
    }

    @Test
    void Should_TerminateSafely_When_ChainBreaks() {
        // task-3 的 retry_of 指向不存在的 task-missing
        given(parsedLogMapper.selectOne(any())).willReturn(log("task-3", "task-missing"), null);

        assertThat(service.traceChain("task-3")).containsExactly("task-3");
    }

    @Test
    void Should_Truncate_When_ExceedingMaxDepth() {
        ReflectionTestUtils.setField(service, "maxDepth", 2);
        given(parsedLogMapper.selectOne(any())).willReturn(
            log("task-3", "task-2"), log("task-2", "task-1"));

        assertThat(service.traceChain("task-3")).containsExactly("task-3", "task-2");
    }

    @Test
    void Should_StopOnCycle_When_RetryOfFormsLoop() {
        // A -> B -> A（环）
        given(parsedLogMapper.selectOne(any())).willReturn(log("A", "B"), log("B", "A"));

        assertThat(service.traceChain("A")).containsExactly("A", "B");
    }

    @Test
    void Should_ReturnEmpty_When_TaskIdBlank() {
        assertThat(service.traceChain(null)).isEmpty();
        assertThat(service.traceChain(" ")).isEmpty();
    }

    private DocumentParsedLog log(String taskId, String retryOfTaskId) {
        DocumentParsedLog log = new DocumentParsedLog();
        log.setTaskId(taskId);
        log.setRetryOfTaskId(retryOfTaskId);
        return log;
    }
}
