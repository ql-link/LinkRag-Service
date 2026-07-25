package com.qingluo.link.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class CacheCompensationMetricsTest {

    @Test
    void recordsLowCardinalityConsumeFailureAndDltMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("meterRegistry", registry);
        CacheCompensationMetrics metrics = new CacheCompensationMetrics(
            beanFactory.getBeanProvider(MeterRegistry.class));

        metrics.recordConsumeFailure("llm_runtime_config", "DELETE_EXHAUSTED");
        metrics.recordDlt("not-a-known-target", "BAD_PAYLOAD");

        assertThat(registry.get("cache_compensation_consume_failure_total")
            .tags("target", "llm_runtime_config", "reason", "DELETE_EXHAUSTED")
            .counter().count()).isEqualTo(1.0d);
        assertThat(registry.get("cache_compensation_dlt_total")
            .tags("target", "unknown", "reason", "BAD_PAYLOAD")
            .counter().count()).isEqualTo(1.0d);
    }
}
