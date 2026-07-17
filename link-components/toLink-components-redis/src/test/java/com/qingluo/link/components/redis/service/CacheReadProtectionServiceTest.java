package com.qingluo.link.components.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CacheReadProtectionServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private CacheAtomicOperations atomicOperations;
    @Mock private CacheMetrics metrics;
    private CacheConsistencyProperties properties;
    private CacheReadProtectionService service;

    @BeforeEach
    void setUp() {
        properties = new CacheConsistencyProperties();
        properties.setTtlJitterSeconds(0);
        properties.setLoadWaitMs(5);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CacheReadProtectionService(redisTemplate, properties, atomicOperations, metrics);
    }

    @Test
    void cacheHit_doesNotCallLoader() {
        when(valueOperations.get("cache:user:profile:1")).thenReturn("cached");
        AtomicInteger loads = new AtomicInteger();
        String result = service.getOrLoad(
            CacheRoute.of("cache:user:profile:1"), String.class, 1, TimeUnit.DAYS,
            () -> {
                loads.incrementAndGet();
                return "db";
            });
        assertThat(result).isEqualTo("cached");
        assertThat(loads).hasValue(0);
        verify(atomicOperations, never()).tryLock(any(), any());
    }

    @Test
    void concurrentMisses_areMergedAndSubsequentReadHitsCache() throws Exception {
        AtomicReference<Object> cache = new AtomicReference<>();
        when(valueOperations.get("cache:user:profile:1")).thenAnswer(invocation -> cache.get());
        when(atomicOperations.tryLock(any(), any())).thenReturn(true);
        when(atomicOperations.readFence(any())).thenReturn(0L);
        when(atomicOperations.writeIfFenceUnchanged(any(), anyLong(), any(), any()))
            .thenAnswer(invocation -> {
                cache.set(invocation.getArgument(2));
                return true;
            });
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        java.util.function.Supplier<String> loader = () -> {
            loads.incrementAndGet();
            loaderStarted.countDown();
            try {
                releaseLoader.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "db";
        };

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.getOrLoad(
                CacheRoute.of("cache:user:profile:1"), String.class, 1, TimeUnit.DAYS, loader));
            loaderStarted.await(2, TimeUnit.SECONDS);
            var second = pool.submit(() -> service.getOrLoad(
                CacheRoute.of("cache:user:profile:1"), String.class, 1, TimeUnit.DAYS, loader));
            releaseLoader.countDown();
            assertThat(List.of(first.get(), second.get())).containsOnly("db");
        } finally {
            pool.shutdownNow();
        }
        assertThat(loads).hasValue(1);
        assertThat(service.getOrLoad(CacheRoute.of("cache:user:profile:1"),
            String.class, 1, TimeUnit.DAYS, () -> "unexpected")).isEqualTo("db");
    }

    @Test
    void redisReadAndWriteFailures_returnDatabaseValueAndRecordOneErrorEach() {
        when(valueOperations.get("cache:dataset:parse-config:10"))
            .thenThrow(new RuntimeException("redis down"));
        when(atomicOperations.tryLock(any(), any())).thenThrow(new RuntimeException("redis down"));
        when(atomicOperations.readFence(any())).thenThrow(new RuntimeException("redis down"));

        String result = service.getOrLoad(CacheRoute.of("cache:dataset:parse-config:10"),
            String.class, 7, TimeUnit.DAYS, () -> "V2");

        assertThat(result).isEqualTo("V2");
        verify(metrics).read("error");
        verify(metrics).write("error");
    }

    @Test
    void changedFence_skipsOldBackfill() {
        when(valueOperations.get("cache:dataset:parse-config:10")).thenReturn(null);
        when(atomicOperations.tryLock(any(), any())).thenReturn(true);
        when(atomicOperations.readFence(any())).thenReturn(3L);
        when(atomicOperations.writeIfFenceUnchanged(any(), anyLong(), any(), any())).thenReturn(false);

        assertThat(service.getOrLoad(CacheRoute.of("cache:dataset:parse-config:10"),
            String.class, 7, TimeUnit.DAYS, () -> "V1")).isEqualTo("V1");
        verify(metrics).write("fence_changed");
    }
}
