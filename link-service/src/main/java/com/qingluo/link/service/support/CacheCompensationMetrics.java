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

    public void recordConsumeFailure(String target, String reason) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(
                "cache_compensation_consume_failure_total",
                "target", normalizeTarget(target),
                "reason", normalizeReason(reason)).increment();
        }
    }

    public void recordDlt(String target, String reason) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(
                "cache_compensation_dlt_total",
                "target", normalizeTarget(target),
                "reason", normalizeReason(reason)).increment();
        }
    }

    private String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return "unknown";
        }
        return switch (target) {
            case "dataset_parse_config", "user_profile", "published_blog_index",
                 "llm_runtime_config" -> target;
            default -> "unknown";
        };
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }
}
