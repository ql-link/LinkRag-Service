package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 用户 LLM 配置缓存 owner service 实现。
 *
 * <p>`llm:u_def:{userId}` 保持原 key 不变，但 value 表示当前用户全部
 * 能力默认映射，格式为 capability -> configId。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigCacheServiceImpl implements UserLLMConfigCacheService {

    static final String CONFIG_KEY_PREFIX = "llm:cfg:";
    static final String DEFAULT_KEY_PREFIX = "llm:u_def:";
    static final long TTL_DAYS = 1L;

    private final CacheConsistencyService cacheConsistencyService;
    private final CacheReadProtectionService cacheReadProtectionService;

    @Override
    public UserLLMConfig getConfigOrLoad(Long configId, Supplier<UserLLMConfig> loader) {
        return cacheReadProtectionService.getOrLoad(
                CONFIG_KEY_PREFIX + configId,
                UserLLMConfig.class,
                TTL_DAYS,
                TimeUnit.DAYS,
                loader
        );
    }

    @Override
    public Map<String, Long> getDefaultConfigIdMapOrLoad(Long userId, Supplier<Map<String, Long>> loader) {
        DefaultConfigMapCacheValue value = cacheReadProtectionService.getOrLoad(
                DEFAULT_KEY_PREFIX + userId,
                DefaultConfigMapCacheValue.class,
                TTL_DAYS,
                TimeUnit.DAYS,
                () -> new DefaultConfigMapCacheValue(loader.get())
        );
        if (value == null || value.getConfigIds() == null) {
            return Map.of();
        }
        return value.getConfigIds();
    }

    @Override
    public void evictConfig(Long configId) {
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
    }

    @Override
    public void evictDefaultMap(Long userId) {
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    @Data
    public static class DefaultConfigMapCacheValue {
        private Map<String, Long> configIds = new HashMap<>();

        public DefaultConfigMapCacheValue() {
        }

        public DefaultConfigMapCacheValue(Map<String, Long> configIds) {
            this.configIds = configIds == null ? new HashMap<>() : new HashMap<>(configIds);
        }
    }
}
