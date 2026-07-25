package com.qingluo.link.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentFileConfigHealthIndicator implements HealthIndicator {

    private final DocumentFileConfigReadiness readiness;

    @Override
    public Health health() {
        try {
            if (readiness.isReady()) {
                return Health.up().withDetail("fingerprint", readiness.fingerprint()).build();
            }
            return Health.down().withDetail("reason", "INSTANCE_UPLOAD_DEFAULTS_MISMATCH").build();
        } catch (RuntimeException ex) {
            return Health.down(ex).withDetail("reason", "REDIS_UNAVAILABLE").build();
        }
    }
}
