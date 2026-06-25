package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 用户 LLM 配置缓存实现。
 *
 * <p>用户配置变更频率低，写路径通过 {@code USER_DEFAULT_LLM_CONFIG} 用户维度统一失效；
 * TTL 只作为兜底自愈，实际一致性依赖写后删缓存和 CDC 补偿。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLLMConfigCacheServiceImpl implements UserLLMConfigCacheService {

    public static final String KEY_PREFIX = "llm:u_cfg:";
    static final long TTL_MINUTES = 60L;

    private final CacheReadProtectionService cacheReadProtectionService;

    @Override
    public List<UserLLMConfigDTO> getOrLoadAll(Long userId, Supplier<List<UserLLMConfigDTO>> loader) {
        AtomicBoolean loadStarted = new AtomicBoolean(false);
        AtomicBoolean loadCompleted = new AtomicBoolean(false);
        AtomicReference<UserLLMConfigSnapshot> loadedValue = new AtomicReference<>();
        Supplier<UserLLMConfigSnapshot> trackedLoader = () -> {
            loadStarted.set(true);
            UserLLMConfigSnapshot value = new UserLLMConfigSnapshot(nullToEmpty(loader.get()));
            loadedValue.set(value);
            loadCompleted.set(true);
            return value;
        };

        try {
            UserLLMConfigSnapshot snapshot = cacheReadProtectionService.getOrLoad(
                    KEY_PREFIX + userId,
                    UserLLMConfigSnapshot.class,
                    TTL_MINUTES,
                    TimeUnit.MINUTES,
                    trackedLoader);
            return snapshot == null ? List.of() : nullToEmpty(snapshot.getConfigs());
        } catch (RuntimeException ex) {
            if (loadCompleted.get()) {
                log.warn("Backfill user LLM config cache failed after database load; return loaded value, userId={}, error={}: {}",
                        userId, ex.getClass().getSimpleName(), ex.getMessage());
                UserLLMConfigSnapshot loaded = loadedValue.get();
                return loaded == null ? List.of() : nullToEmpty(loaded.getConfigs());
            }
            if (loadStarted.get()) {
                throw ex;
            }
            log.warn("Read-through user LLM config cache failed; fallback to database, userId={}, error={}: {}",
                    userId, ex.getClass().getSimpleName(), ex.getMessage());
            return nullToEmpty(loader.get());
        }
    }

    private List<UserLLMConfigDTO> nullToEmpty(List<UserLLMConfigDTO> configs) {
        return configs == null ? List.of() : configs;
    }
}
