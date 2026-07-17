package com.qingluo.link.components.redis.service;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

/**
 * 把慢回填 fence、分布式加载锁和失效操作集中为 Redis 原子操作。
 */
@Component
public class CacheAtomicOperations {

    private static final byte[] RELEASE_LOCK_SCRIPT = bytes("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
          return redis.call('DEL', KEYS[1])
        end
        return 0
        """);

    private static final byte[] WRITE_IF_FENCE_SCRIPT = bytes("""
        local current = redis.call('GET', KEYS[2])
        if not current then current = '0' end
        if current == ARGV[1] then
          redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
          return 1
        end
        return 0
        """);

    private static final byte[] INVALIDATE_SCRIPT = bytes("""
        local version = redis.call('INCR', KEYS[2])
        redis.call('EXPIRE', KEYS[2], ARGV[1])
        redis.call('DEL', KEYS[1])
        return version
        """);

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheConsistencyProperties properties;

    public CacheAtomicOperations(RedisTemplate<String, Object> redisTemplate,
                                 CacheConsistencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean tryLock(CacheRoute route, String token) {
        Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection ->
            connection.set(
                key(route.lockKey()),
                bytes(token),
                Expiration.milliseconds(properties.getLoadLockTtlMs()),
                SetOption.SET_IF_ABSENT));
        return Boolean.TRUE.equals(result);
    }

    public void releaseLock(CacheRoute route, String token) {
        redisTemplate.execute((RedisCallback<Long>) connection ->
            eval(connection, RELEASE_LOCK_SCRIPT, 1, key(route.lockKey()), bytes(token)));
    }

    public long readFence(CacheRoute route) {
        byte[] raw = redisTemplate.execute((RedisCallback<byte[]>) connection -> connection.get(key(route.fenceKey())));
        if (raw == null || raw.length == 0) {
            return 0L;
        }
        return Long.parseLong(new String(raw, StandardCharsets.UTF_8));
    }

    public boolean writeIfFenceUnchanged(CacheRoute route, long expectedFence,
                                         Object value, Duration ttl) {
        byte[] serializedValue = serializeValue(value);
        Long result = redisTemplate.execute((RedisCallback<Long>) connection ->
            eval(connection, WRITE_IF_FENCE_SCRIPT, 2,
                key(route.dataKey()),
                key(route.fenceKey()),
                bytes(String.valueOf(expectedFence)),
                serializedValue,
                bytes(String.valueOf(Math.max(1L, ttl.getSeconds())))));
        return Objects.equals(result, 1L);
    }

    public long invalidate(CacheRoute route) {
        Long result = redisTemplate.execute((RedisCallback<Long>) connection ->
            eval(connection, INVALIDATE_SCRIPT, 2,
                key(route.dataKey()),
                key(route.fenceKey()),
                bytes(String.valueOf(properties.getFenceTtlSeconds()))));
        return result == null ? 0L : result;
    }

    private Long eval(RedisConnection connection, byte[] script, int keyCount, byte[]... values) {
        return connection.eval(script, org.springframework.data.redis.connection.ReturnType.INTEGER, keyCount, values);
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeValue(Object value) {
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        byte[] serialized = serializer.serialize(value);
        if (serialized == null) {
            throw new IllegalArgumentException("Cache value cannot serialize to null");
        }
        return serialized;
    }

    private byte[] key(String value) {
        byte[] serialized = redisTemplate.getStringSerializer().serialize(value);
        if (serialized == null) {
            throw new IllegalArgumentException("Cache key cannot serialize to null");
        }
        return serialized;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
