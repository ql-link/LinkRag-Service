package com.qingluo.link.components.redis.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 缓存基础指标；没有 MeterRegistry 时自动退化为无操作。
 */
@Component
public class CacheMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    public CacheMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    public void read(String outcome) {
        increment("tolink.cache.read", outcome);
    }

    public void write(String outcome) {
        increment("tolink.cache.write", outcome);
    }

    public void firstDelete(String outcome) {
        increment("tolink.cache.first_delete", outcome);
    }

    public void compensationDelete(String outcome) {
        increment("tolink.cache.compensation_delete", outcome);
    }

    private void increment(String name, String outcome) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(name, "outcome", outcome == null ? "unknown" : outcome).increment();
        }
    }
}
