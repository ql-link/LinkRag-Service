package com.qingluo.link.components.redis;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 */
@Slf4j
public class RedisUtils {

    private static RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisUtils.redisTemplate = redisTemplate;
    }

    // ==================== 常量 ====================

    public static final int EXRP_ONE_MINUTE = 60;          // 一分钟
    public static final int EXRP_ONE_HOUR = 60 * 60;        // 一小时
    public static final int EXRP_ONE_DAY = 60 * 60 * 24;    // 一天
    public static final int EXRP_ONE_MONTH = 60 * 60 * 24 * 30; // 一个月

    // ==================== String 操作 ====================

    public static boolean set(String key, Object value, int seconds) {
        try {
            redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Redis set error, key: {}", key, e);
            return false;
        }
    }

    public static boolean set(String key, String value, int seconds) {
        return set(key, (Object) value, seconds);
    }

    public static boolean remove(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("Redis remove error, key: {}", key, e);
            return false;
        }
    }

    public static String get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Redis get error, key: {}", key, e);
            return null;
        }
    }

    public static <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return clazz.cast(value);
            }
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Redis get error, key: {}, type: {}", key, clazz.getName(), e);
            return null;
        }
    }

    // ==================== List 操作 ====================

    public static <T> List<T> getList(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("Redis getList error, key: {}", key, e);
            return null;
        }
    }

    // ==================== 过期时间 ====================

    public static boolean expire(String key, int seconds) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, seconds, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.error("Redis expire error, key: {}", key, e);
            return false;
        }
    }

    public static Long getExpire(String key) {
        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis getExpire error, key: {}", key, e);
            return null;
        }
    }

    // ==================== 判断是否存在 ====================

    public static boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis exists error, key: {}", key, e);
            return false;
        }
    }

    // ==================== 自增/自减 ====================

    public static Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("Redis increment error, key: {}", key, e);
            return null;
        }
    }

    public static Long decrement(String key) {
        try {
            return redisTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            log.error("Redis decrement error, key: {}", key, e);
            return null;
        }
    }

    // ==================== Hash 操作 ====================

    public static boolean hSet(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            log.error("Redis hSet error, key: {}, field: {}", key, field, e);
            return false;
        }
    }

    public static Object hGet(String key, String field) {
        try {
            return redisTemplate.opsForHash().get(key, field);
        } catch (Exception e) {
            log.error("Redis hGet error, key: {}, field: {}", key, field, e);
            return null;
        }
    }

    public static boolean hDel(String key, String... fields) {
        try {
            redisTemplate.opsForHash().delete(key, (Object[]) fields);
            return true;
        } catch (Exception e) {
            log.error("Redis hDel error, key: {}", key, e);
            return false;
        }
    }

    public static boolean hExists(String key, String field) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, field));
        } catch (Exception e) {
            log.error("Redis hExists error, key: {}, field: {}", key, field, e);
            return false;
        }
    }
}
