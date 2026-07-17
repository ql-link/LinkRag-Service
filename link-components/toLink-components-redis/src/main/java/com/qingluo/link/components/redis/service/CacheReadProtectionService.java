package com.qingluo.link.components.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 统一缓存读保护服务。
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
    private final CacheAtomicOperations atomicOperations;
    private final CacheMetrics metrics;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    public <T> T getOrLoad(String cacheKey, Class<T> clazz, long ttl, TimeUnit ttlUnit, Supplier<T> loader) {
        return getOrLoad(CacheRoute.of(cacheKey), clazz, ttl, ttlUnit, loader);
    }

    /**
     * 数据库镜像缓存读取：本地合并并发、跨实例加载锁、fence 条件回填，Redis 故障时回源。
     */
    public <T> T getOrLoad(CacheRoute route, Class<T> clazz, long ttl,
                           TimeUnit ttlUnit, Supplier<T> loader) {
        CacheLookup<T> first = readQuietly(route.dataKey(), clazz);
        if (first.hit()) {
            return first.value();
        }

        ReentrantLock localLock = keyLocks.computeIfAbsent(route.dataKey(), ignored -> new ReentrantLock());
        localLock.lock();
        try {
            if (!first.error()) {
                CacheLookup<T> doubleChecked = readQuietly(route.dataKey(), clazz);
                if (doubleChecked.hit()) {
                    return doubleChecked.value();
                }
            }

            String token = UUID.randomUUID().toString();
            boolean distributedLocked = false;
            try {
                distributedLocked = atomicOperations.tryLock(route, token);
            } catch (RuntimeException ex) {
                log.warn("Cache load coordination failed key={}, fallback to loader: {}",
                    route.dataKey(), ex.getMessage());
            }
            try {
                if (!distributedLocked && !first.error()) {
                    sleepSilently(properties.getLoadWaitMs());
                    CacheLookup<T> retried = readQuietly(route.dataKey(), clazz);
                    if (retried.hit()) {
                        return retried.value();
                    }
                }
                return loadAndBackfill(route, ttl, ttlUnit, loader);
            } finally {
                if (distributedLocked) {
                    try {
                        atomicOperations.releaseLock(route, token);
                    } catch (RuntimeException ex) {
                        log.warn("Release cache load lock failed key={}: {}", route.lockKey(), ex.getMessage());
                    }
                }
            }
        } finally {
            localLock.unlock();
            if (!localLock.hasQueuedThreads()) {
                keyLocks.remove(route.dataKey(), localLock);
            }
        }
    }

    public <T> T getIfPresent(String cacheKey, Class<T> clazz) {
        return readValue(cacheKey, clazz).value();
    }

    /**
     * 保留已有批量读取能力；当前三个业务 owner 均使用单 key fence 读取。
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

    private <T> CacheLookup<T> readQuietly(String cacheKey, Class<T> clazz) {
        try {
            CacheLookup<T> lookup = readValue(cacheKey, clazz);
            metrics.read(lookup.hit() ? "hit" : "miss");
            return lookup;
        } catch (RuntimeException ex) {
            metrics.read("error");
            log.warn("Read cache failed key={}, fallback to loader: {}", cacheKey, ex.getMessage());
            return CacheLookup.failed();
        }
    }

    private <T> CacheLookup<T> readValue(String cacheKey, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value == null) {
            return CacheLookup.miss();
        }
        if (Objects.equals(value, NULL_MARKER)) {
            return CacheLookup.hit(null);
        }
        return CacheLookup.hit(toType(value, clazz));
    }

    private <T> T loadAndBackfill(CacheRoute route, long ttl, TimeUnit ttlUnit, Supplier<T> loader) {
        long fence;
        try {
            fence = atomicOperations.readFence(route);
        } catch (RuntimeException ex) {
            T loaded = loader.get();
            metrics.write("error");
            log.warn("Read cache fence failed key={}, skip backfill: {}", route.fenceKey(), ex.getMessage());
            return loaded;
        }

        T loaded = loader.get();
        long ttlSeconds = loaded == null
            ? properties.getNullCacheTtlSeconds()
            : withJitter(ttlUnit.toSeconds(ttl));
        Object cacheValue = loaded == null ? NULL_MARKER : loaded;
        try {
            boolean written = atomicOperations.writeIfFenceUnchanged(
                route, fence, cacheValue, Duration.ofSeconds(Math.max(1L, ttlSeconds)));
            metrics.write(written ? "success" : "fence_changed");
        } catch (RuntimeException ex) {
            metrics.write("error");
            log.warn("Backfill cache failed key={}, ignored: {}", route.dataKey(), ex.getMessage());
        }
        return loaded;
    }

    private void backfillQuietly(String cacheKey, Object value, long ttl, TimeUnit ttlUnit) {
        try {
            writeLoadedValue(cacheKey, value, ttl, ttlUnit);
        } catch (RuntimeException ex) {
            log.warn("Backfill cache key {} failed, ignored: {}: {}", cacheKey,
                ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private <T> T toType(Object value, Class<T> clazz) {
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return OBJECT_MAPPER.convertValue(value, clazz);
    }

    private void writeLoadedValue(String cacheKey, Object loaded, long ttl, TimeUnit ttlUnit) {
        if (loaded == null) {
            redisTemplate.opsForValue().set(cacheKey, NULL_MARKER,
                properties.getNullCacheTtlSeconds(), TimeUnit.SECONDS);
            return;
        }
        redisTemplate.opsForValue().set(cacheKey, loaded, Duration.ofSeconds(withJitter(ttlUnit.toSeconds(ttl))));
    }

    private long withJitter(long ttlSeconds) {
        long jitterSeconds = properties.getTtlJitterSeconds();
        return jitterSeconds > 0
            ? ttlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1)
            : ttlSeconds;
    }

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

    private record CacheLookup<T>(boolean hit, T value, boolean error) {

        private static <T> CacheLookup<T> hit(T value) {
            return new CacheLookup<>(true, value, false);
        }

        private static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(false, null, false);
        }

        private static <T> CacheLookup<T> failed() {
            return new CacheLookup<>(false, null, true);
        }
    }
}
