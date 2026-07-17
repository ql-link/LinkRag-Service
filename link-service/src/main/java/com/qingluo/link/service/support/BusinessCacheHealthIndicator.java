package com.qingluo.link.service.support;

import com.qingluo.link.service.cache.BusinessCacheReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessCacheHealthIndicator implements HealthIndicator {

    private final BusinessCacheReadiness readiness;

    @Override
    public Health health() {
        if (readiness.isDatabaseMirrorCacheEnabled()) {
            return Health.up().withDetail("databaseMirrorCache", "ENABLED").build();
        }
        return Health.unknown()
            .withDetail("databaseMirrorCache", "DISABLED")
            .withDetail("reason", readiness.notReadyReason())
            .build();
    }
}
