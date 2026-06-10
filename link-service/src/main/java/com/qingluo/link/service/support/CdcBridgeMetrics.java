package com.qingluo.link.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * CDC 桥接消费兜底的监控指标封装。
 *
 * <p>通过 {@link ObjectProvider} 注入 {@link MeterRegistry}，未引入 Micrometer/Actuator 时
 * 降级为无操作（仅靠调用方日志），保证可编译、可测、可独立运行。</p>
 *
 * <ul>
 *   <li>{@code tolink.cdc_bridge.recover}（tag reason）：错误处理器 recover 计数。</li>
 * </ul>
 */
@Component
public class CdcBridgeMetrics {

    static final String RECOVER_METRIC = "tolink.cdc_bridge.recover";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public CdcBridgeMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * 记录一次错误处理器 recover（提交跳过）。
     *
     * @param reason 分类原因，如 bad_payload / infra_exhausted
     */
    public void recordRecover(String reason) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(RECOVER_METRIC, "reason", reason == null ? "unknown" : reason).increment();
        }
    }
}
