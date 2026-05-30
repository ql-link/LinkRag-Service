package com.qingluo.link.service.recall;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.service.config.RecallProperties;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 按用户固定窗口限流（acceptance 场景 6/7）：窗口内允许突发到上限、超限拒绝；
 * 不同用户独立计数；窗口滚动后计数重置。
 */
class RecallRateLimiterTest {

    private RecallProperties propsWithRate(int perMinute) {
        RecallProperties properties = new RecallProperties();
        properties.setRateLimitPerMinute(perMinute);
        return properties;
    }

    @Test
    @DisplayName("Should_AllowBurstUpToLimitThenReject_When_WithinWindow")
    void Should_AllowBurstUpToLimitThenReject_When_WithinWindow() {
        RecallRateLimiter limiter = new RecallRateLimiter(propsWithRate(3));

        // 同一窗口内允许连发到上限（突发），第 4 次才被拒。
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
    }

    @Test
    @DisplayName("Should_IsolatePerUser_When_OneUserExhausted")
    void Should_IsolatePerUser_When_OneUserExhausted() {
        RecallRateLimiter limiter = new RecallRateLimiter(propsWithRate(1));

        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    @DisplayName("Should_ResetCount_When_WindowElapsed")
    void Should_ResetCount_When_WindowElapsed() {
        AtomicLong clock = new AtomicLong(10_000L);
        RecallRateLimiter limiter = new RecallRateLimiter(propsWithRate(1), clock::get);

        assertThat(limiter.tryAcquire(1L)).isTrue();   // 窗口 1：放行
        assertThat(limiter.tryAcquire(1L)).isFalse();  // 窗口 1：超限
        clock.addAndGet(60_000L);                      // 进入下一窗口
        assertThat(limiter.tryAcquire(1L)).isTrue();   // 计数重置后可用
    }
}
