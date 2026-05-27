package com.qingluo.link.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheServiceImpl implements UserCacheService {

    static final String KEY_PREFIX = "user:info:";
    static final long TTL_DAYS = 7L;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheConsistencyService cacheConsistencyService;
    private final CacheReadProtectionService cacheReadProtectionService;

    @Override
    public void put(Long userId, UserProfileDTO dto) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, dto, TTL_DAYS, TimeUnit.DAYS);
        } catch (RuntimeException ex) {
            log.warn("Put user profile cache failed; continue without cache, userId={}, error={}: {}",
                    userId, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    @Override
    public UserProfileDTO get(Long userId) {
        Object value;
        try {
            value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        } catch (RuntimeException ex) {
            log.warn("Read user profile cache failed; treat as cache miss, userId={}, error={}: {}",
                    userId, ex.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
        if (value == null) {
            return null;
        }
        if (value instanceof UserProfileDTO) {
            return (UserProfileDTO) value;
        }
        return OBJECT_MAPPER.convertValue(value, UserProfileDTO.class);
    }

    @Override
    public UserProfileDTO getOrLoad(Long userId, Supplier<UserProfileDTO> loader) {
        AtomicBoolean loadStarted = new AtomicBoolean(false);
        AtomicBoolean loadCompleted = new AtomicBoolean(false);
        AtomicReference<UserProfileDTO> loadedValue = new AtomicReference<>();
        Supplier<UserProfileDTO> trackedLoader = () -> {
            loadStarted.set(true);
            UserProfileDTO value = loader.get();
            loadedValue.set(value);
            loadCompleted.set(true);
            return value;
        };
        try {
            return cacheReadProtectionService.getOrLoad(
                    KEY_PREFIX + userId,
                    UserProfileDTO.class,
                    TTL_DAYS,
                    TimeUnit.DAYS,
                    trackedLoader
            );
        } catch (RuntimeException ex) {
            if (loadCompleted.get()) {
                log.warn("Backfill user profile cache failed after database load; return loaded value, userId={}, error={}: {}",
                        userId, ex.getClass().getSimpleName(), ex.getMessage());
                return loadedValue.get();
            }
            if (loadStarted.get()) {
                throw ex;
            }
            log.warn("Read-through user profile cache failed; fallback to database, userId={}, error={}: {}",
                    userId, ex.getClass().getSimpleName(), ex.getMessage());
            return loader.get();
        }
    }

    @Override
    public void evict(Long userId) {
        cacheConsistencyService.evict(CacheEvictTarget.USER, userId);
    }
}
