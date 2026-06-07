package com.qingluo.link.core.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 透传装饰器：把提交线程的 MDC（含 traceId）复制到执行线程，使异步任务日志与发起请求同链路。
 *
 * <p>用于 {@code ThreadPoolTaskExecutor.setTaskDecorator}。捕获的是提交时刻的快照；任务执行结束
 * 清理执行线程 MDC，避免池线程复用时上下文残留串号。</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
