package com.qingluo.link.service.recall;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.qingluo.link.service.config.RecallProperties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 按登录用户的单机限流（决策④）。
 *
 * <p>采用<b>固定时间窗口计数</b>：每个用户在 60 秒窗口内最多放行 {@code rate-limit-per-minute} 次，
 * <b>允许窗口内突发</b>（连续发满上限），第 N+1 次返回 {@code false}（调用方按建流前 429 拒绝）；
 * 窗口滚动后计数清零。用 Guava {@link Cache}（{@code expireAfterAccess}）持有各用户窗口，
 * 闲置自动回收避免内存泄漏。按 userId 隔离，不同用户互不影响（acceptance 场景 6/7）。</p>
 *
 * <p>区别于匀速令牌桶：这里是「每分钟 N 次、可连发」，符合 brief「每用户每分钟 N 次」的直觉语义，
 * 不会因匀速节流而把 6 秒内的第二次正常召回误拒。</p>
 */
@Component
public class RecallRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerWindow;
    private final LongSupplier clock;
    private final Cache<Long, Window> windows;

    @Autowired
    public RecallRateLimiter(RecallProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /** 包级构造器：注入时钟以便测试窗口滚动。 */
    RecallRateLimiter(RecallProperties properties, LongSupplier clock) {
        this.limitPerWindow = properties.getRateLimitPerMinute();
        this.clock = clock;
        this.windows = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();
    }

    /**
     * 尝试为该用户记一次召回，非阻塞。当前窗口内未超上限返回 {@code true}，超限返回 {@code false}。
     */
    public boolean tryAcquire(Long userId) {
        try {
            return windows.get(userId, Window::new).tryAcquire(clock.getAsLong(), limitPerWindow);
        } catch (ExecutionException e) {
            // 限流器自身异常不应阻断正常召回。
            return true;
        }
    }

    /** 单个用户的固定窗口计数器（自身同步，保证并发请求计数正确）。 */
    private static final class Window {

        private long windowStart;
        private int count;

        synchronized boolean tryAcquire(long now, int limit) {
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
