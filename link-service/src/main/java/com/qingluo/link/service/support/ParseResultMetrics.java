package com.qingluo.link.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * parse_result 消费兜底的监控指标封装。
 *
 * <p>通过 {@link ObjectProvider} 注入 {@link MeterRegistry}，未引入 Micrometer/Actuator
 * 时降级为无操作（仅靠调用方日志），保证可编译、可测、可独立运行。</p>
 *
 * <ul>
 *   <li>{@code tolink.parse_result.recover}（tag reason）：错误处理器 recover 计数。</li>
 *   <li>{@code tolink.parse_result.stuck}：卡住扫描命中仍为 created 的任务计数。</li>
 *   <li>{@code tolink.parse_result.repushed}：以 DB 为准补推终态 SSE 的计数。</li>
 * </ul>
 */
@Component
public class ParseResultMetrics {

    static final String RECOVER_METRIC = "tolink.parse_result.recover";
    static final String STUCK_METRIC = "tolink.parse_result.stuck";
    static final String REPUSHED_METRIC = "tolink.parse_result.repushed";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public ParseResultMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * 记录一次错误处理器 recover（提交跳过）。
     *
     * @param reason 分类原因，如 non_retryable / pending_exhausted / infra_exhausted / bad_payload
     */
    public void recordRecover(String reason) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(RECOVER_METRIC, "reason", reason == null ? "unknown" : reason).increment();
        }
    }

    /** 记录一次卡住任务（仍为 created）命中。 */
    public void recordStuck() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(STUCK_METRIC).increment();
        }
    }

    /** 记录一次以 DB 为准的终态 SSE 补推。 */
    public void recordRepushed() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(REPUSHED_METRIC).increment();
        }
    }
}
