package com.qingluo.link.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.qingluo.link.service.cache.LLMRuntimeCacheReadiness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class LLMRuntimeCacheHealthIndicatorTest {

    @Mock private LLMRuntimeCacheReadiness readiness;

    @Test
    void ready_reportsUpWithStableDetails() {
        when(readiness.isEnabled()).thenReturn(true);

        assertThat(new LLMRuntimeCacheHealthIndicator(readiness).health())
            .satisfies(health -> {
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails())
                    .containsEntry("llmRuntimeCache", "ENABLED")
                    .containsEntry("reason", "READY");
            });
    }

    @Test
    void notReady_reportsUnknownWithoutAffectingApplicationHealth() {
        when(readiness.isEnabled()).thenReturn(false);
        when(readiness.notReadyReason()).thenReturn("LLM_RUNTIME_CONSUMER_TARGET_NOT_READY");

        assertThat(new LLMRuntimeCacheHealthIndicator(readiness).health())
            .satisfies(health -> {
                assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
                assertThat(health.getDetails())
                    .containsEntry("llmRuntimeCache", "DISABLED")
                    .containsEntry("reason", "LLM_RUNTIME_CONSUMER_TARGET_NOT_READY");
            });
    }
}
