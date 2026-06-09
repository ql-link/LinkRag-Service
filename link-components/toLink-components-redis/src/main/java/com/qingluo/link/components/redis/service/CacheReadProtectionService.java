package com.qingluo.link.components.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
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
     * 批量读缓存：一次 MGET 读取多个 key，命中的直接返回，缺失的交给 batchLoader 批量回源并逐 key 回填。
     *
     * <p>穿透防护：回源后对仍无值的 key 写空值占位；雪崩防护：回填真实值带 TTL 抖动。
     * 读故障（MGET 抛异常）向上抛出，由调用方决定降级；回填故障（写 Redis 抛）被吞掉，
     * 不影响本次已加载值的返回。批量场景不做单 key 回源合并（击穿）。</p>
     *
     * @param batchLoader 入参为缺失的 cacheKey 列表，返回 cacheKey→value；未返回的 key 视为空（写空值占位）
     * @return 命中或回源得到的 cacheKey→value（空值占位的 key 不出现在结果中）
     */
    public <T> Map<String, T> getOrLoadBatch(List<String> cacheKeys, Class<T> clazz,
                                             long ttl, TimeUnit ttlUnit,
                                             Function<List<String>, Map<String, T>> batchLoader) {
        Map<String, T> result = new LinkedHashMap<>();
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return result;
        }
        List<String> missing = new ArrayList<>();
        List<Object> raws = redisTemplate.opsForValue().multiGet(cacheKeys);
        for (int i = 0; i < cacheKeys.size(); i++) {
            String key = cacheKeys.get(i);
            Object raw = raws == null ? null : raws.get(i);
            if (raw == null) {
                missing.add(key);
            } else if (!Objects.equals(raw, NULL_MARKER)) {
                result.put(key, toType(raw, clazz));
            }
            // NULL_MARKER：空值占位命中，既不算缺失也不计入结果
        }
        if (missing.isEmpty()) {
            return result;
        }
        Map<String, T> loaded = batchLoader.apply(missing);
        for (String key : missing) {
            T value = loaded == null ? null : loaded.get(key);
            backfillQuietly(key, value, ttl, ttlUnit);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 回填单 key，写失败只记日志不外抛（回填故障不影响可用性，区别于读故障的上抛降级）。
     */
    private void backfillQuietly(String cacheKey, Object value, long ttl, TimeUnit ttlUnit) {
        try {
            writeLoadedValue(cacheKey, value, ttl, ttlUnit);
        } catch (RuntimeException ex) {
            log.warn("Backfill cache key {} failed, ignored: {}: {}", cacheKey,
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    /**
     * 从 Redis 读取并转换成目标类型。
     */
    private <T> T readValue(String cacheKey, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value == null || Objects.equals(value, NULL_MARKER)) {
            return null;
        }
        return toType(value, clazz);
    }

    /**
     * 将 Redis 原始值转换成目标类型：已是目标类型直接转型，否则用 Jackson 转换（兼容反序列化为 Map 的情形）。
     */
    private <T> T toType(Object value, Class<T> clazz) {
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
