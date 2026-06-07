package com.qingluo.link.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MdcTaskDecorator} 单测：覆盖提交线程 MDC 透传到执行线程，以及任务结束后清理（防线程复用串号）。
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void decorate_should_propagate_submitter_mdc_to_runnable() {
        MDC.put(TraceContext.TRACE_ID_KEY, "trace-propagated");
        AtomicReference<String> seen = new AtomicReference<>();

        Runnable decorated = decorator.decorate(
            () -> seen.set(MDC.get(TraceContext.TRACE_ID_KEY)));
        // 在另一个线程执行，验证透传的是提交时的快照而非线程默认空 MDC
        runOnFreshThread(decorated);

        assertEquals("trace-propagated", seen.get());
    }

    @Test
    void decorate_should_clear_mdc_after_run() {
        MDC.put(TraceContext.TRACE_ID_KEY, "trace-x");
        AtomicReference<String> afterRunOnWorker = new AtomicReference<>("sentinel");

        Runnable decorated = decorator.decorate(() -> {
            // 模拟池线程上一次任务残留，run 内可见透传值
        });
        // 复用同一线程：先放残留，再跑装饰任务，跑完应被清理
        Thread worker = new Thread(() -> {
            MDC.put(TraceContext.TRACE_ID_KEY, "leftover");
            decorated.run();
            afterRunOnWorker.set(MDC.get(TraceContext.TRACE_ID_KEY));
        });
        joinQuietly(worker);

        assertNull(afterRunOnWorker.get());
    }

    @Test
    void decorate_should_clear_mdc_even_when_task_throws() {
        MDC.put(TraceContext.TRACE_ID_KEY, "trace-boom");
        AtomicReference<String> afterRunOnWorker = new AtomicReference<>("sentinel");

        Runnable decorated = decorator.decorate(() -> {
            throw new IllegalStateException("boom");
        });
        Thread worker = new Thread(() -> {
            try {
                assertThrows(IllegalStateException.class, decorated::run);
            } finally {
                afterRunOnWorker.set(MDC.get(TraceContext.TRACE_ID_KEY));
            }
        });
        joinQuietly(worker);

        assertNull(afterRunOnWorker.get());
    }

    private void runOnFreshThread(Runnable r) {
        joinQuietly(new Thread(r));
    }

    private void joinQuietly(Thread t) {
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
