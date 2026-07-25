package com.qingluo.link.service.support;

import com.qingluo.link.service.cache.LLMRuntimeCacheReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LLMRuntimeCacheHealthIndicator implements HealthIndicator {

    private final LLMRuntimeCacheReadiness readiness;

    @Override
    public Health health() {
        if (readiness.isEnabled()) {
            return Health.up()
                .withDetail("llmRuntimeCache", "ENABLED")
                .withDetail("reason", "READY")
                .build();
        }
        return Health.unknown()
            .withDetail("llmRuntimeCache", "DISABLED")
            .withDetail("reason", readiness.notReadyReason())
            .build();
    }
}
