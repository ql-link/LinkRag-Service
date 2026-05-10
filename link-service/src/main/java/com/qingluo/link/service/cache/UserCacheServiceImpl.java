package com.qingluo.link.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
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
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, dto, TTL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public UserProfileDTO get(Long userId) {
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
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
        return cacheReadProtectionService.getOrLoad(
                KEY_PREFIX + userId,
                UserProfileDTO.class,
                TTL_DAYS,
                TimeUnit.DAYS,
                loader
        );
    }

    @Override
    public void evict(Long userId) {
        cacheConsistencyService.evict(CacheEvictTarget.USER, userId);
    }
}
