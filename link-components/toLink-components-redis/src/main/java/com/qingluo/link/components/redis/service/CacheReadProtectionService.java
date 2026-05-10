package com.qingluo.link.components.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 统一缓存读保护服务。
 *
 * <p>集中处理缓存穿透、缓存击穿和缓存雪崩的基础防护，避免各业务缓存 owner
 * service 重复实现空值缓存、单 key 回源合并和 TTL 抖动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheReadProtectionService {

    static final String NULL_MARKER = "__NULL__";

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheConsistencyProperties properties;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    /**
     * 统一读缓存入口。
     *
     * <p>优先读缓存；未命中时通过单 key 锁合并并发回源；查库为空时写入空值占位；
     * 回填真实值时增加 TTL 抖动。</p>
     */
    public <T> T getOrLoad(String cacheKey, Class<T> clazz, long ttl, TimeUnit ttlUnit, Supplier<T> loader) {
        T cached = readValue(cacheKey, clazz);
        if (cached != null || isNullMarkerPresent(cacheKey)) {
            return cached;
        }

        ReentrantLock lock = keyLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        if (lock.tryLock()) {
            try {
                T doubleChecked = readValue(cacheKey, clazz);
                if (doubleChecked != null || isNullMarkerPresent(cacheKey)) {
                    return doubleChecked;
                }
                T loaded = loader.get();
                writeLoadedValue(cacheKey, loaded, ttl, ttlUnit);
                return loaded;
            } finally {
                lock.unlock();
            }
        }

        sleepSilently(properties.getLoadWaitMs());
        T retried = readValue(cacheKey, clazz);
        if (retried != null || isNullMarkerPresent(cacheKey)) {
            return retried;
        }

        T loaded = loader.get();
        writeLoadedValue(cacheKey, loaded, ttl, ttlUnit);
        return loaded;
    }

    /**
     * 从 Redis 读取并转换成目标类型。
     */
    private <T> T readValue(String cacheKey, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value == null) {
            return null;
        }
        if (Objects.equals(value, NULL_MARKER)) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return OBJECT_MAPPER.convertValue(value, clazz);
    }

    /**
     * 判断当前 key 是否已经被空值占位。
     */
    private boolean isNullMarkerPresent(String cacheKey) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        return Objects.equals(value, NULL_MARKER);
    }

    /**
     * 回填真实值或空值占位。
     */
    private void writeLoadedValue(String cacheKey, Object loaded, long ttl, TimeUnit ttlUnit) {
        if (loaded == null) {
            redisTemplate.opsForValue().set(cacheKey, NULL_MARKER,
                    properties.getNullCacheTtlSeconds(), TimeUnit.SECONDS);
            return;
        }
        long ttlSeconds = ttlUnit.toSeconds(ttl);
        long jitterSeconds = properties.getTtlJitterSeconds();
        long finalTtl = jitterSeconds > 0
                ? ttlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1)
                : ttlSeconds;
        redisTemplate.opsForValue().set(cacheKey, loaded, Duration.ofSeconds(finalTtl));
    }

    /**
     * 未拿到回源执行权的线程短暂等待，再尝试重读缓存。
     */
    private void sleepSilently(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
