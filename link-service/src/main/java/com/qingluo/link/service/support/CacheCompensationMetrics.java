package com.qingluo.link.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CacheCompensationMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    public CacheCompensationMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    public void failure(String reason) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("tolink.cache.compensation.failure", "reason", reason).increment();
            if ("UNKNOWN_TARGET".equals(reason)) {
                registry.counter("tolink.cache.compensation.unknown_target").increment();
            }
        }
    }
}
