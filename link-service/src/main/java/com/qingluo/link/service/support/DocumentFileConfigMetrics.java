package com.qingluo.link.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DocumentFileConfigMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    public DocumentFileConfigMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    public void fallback(String reason) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("tolink.upload_config.fallback", "reason", reason).increment();
        }
    }
}
