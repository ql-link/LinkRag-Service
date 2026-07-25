package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileCache {

    private final CacheReadProtectionService readProtectionService;
    private final CacheConsistencyService consistencyService;
    private final CacheKeyRouter keyRouter;
    private final BusinessCacheProperties properties;
    private final BusinessCacheReadiness readiness;

    public UserProfileDTO get(Long userId, Supplier<UserProfileDTO> loader) {
        if (!readiness.isDatabaseMirrorCacheEnabled()) {
            return loader.get();
        }
        return readProtectionService.getOrLoad(
            keyRouter.route(CacheEvictTarget.USER_PROFILE, String.valueOf(userId)),
            UserProfileDTO.class,
            properties.getUserProfileTtl().toSeconds(),
            TimeUnit.SECONDS,
            loader);
    }

    public void evict(Long userId) {
        consistencyService.evict(CacheEvictTarget.USER_PROFILE, userId);
    }
}
