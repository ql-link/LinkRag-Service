package com.qingluo.link.service.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Date;

/**
 * 双删缓存服务
 * 缓存策略：更新配置时先删缓存，再更新DB，然后延迟再删一次
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DoubleDeleteCacheService {

    private static final int FIRST_DELETE_MAX_RETRIES = 3;
    private static final long FIRST_DELETE_RETRY_INTERVAL_MS = 200;
    private static final long SECOND_DELETE_DELAY_MS = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ThreadPoolTaskScheduler taskScheduler;

    /**
     * 驱逐配置缓存（双删策略）
     */
    public void evictConfigCache(String configId) {
        String cacheKey = buildConfigCacheKey(configId);

        // 1. 第一删（同步，带重试）
        boolean firstDeleteSuccess = deleteCacheWithRetry(cacheKey);
        if (!firstDeleteSuccess) {
            log.warn("第一删失败，依赖第二删兜底: {}", cacheKey);
        }

        // 2. 延迟第二删（1秒后）
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐用户默认配置缓存
     */
    public void evictDefaultConfigCache(String userId) {
        String cacheKey = "llm:u_def:" + userId;
        deleteCacheWithRetry(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐系统厂商缓存
     */
    public void evictProviderCache(String providerType) {
        String cacheKey = "llm:pvd:" + providerType;
        deleteCacheWithRetry(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 带重试的缓存删除（第一删）
     */
    public boolean deleteCacheWithRetry(String cacheKey) {
        for (int i = 0; i < FIRST_DELETE_MAX_RETRIES; i++) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.delete(cacheKey))) {
                    log.debug("第一删成功: {}", cacheKey);
                    return true;
                }
                // key 不存在也算删除成功
                return true;
            } catch (Exception e) {
                log.warn("第一删第{}次失败: {}, error: {}", i + 1, cacheKey, e.getMessage());
                if (i < FIRST_DELETE_MAX_RETRIES - 1) {
                    sleepSilently(FIRST_DELETE_RETRY_INTERVAL_MS);
                }
            }
        }
        return false;
    }

    /**
     * 延迟第二删
     */
    private void scheduleSecondDelete(String cacheKey) {
        taskScheduler.schedule(() -> {
            try {
                deleteCache(cacheKey);
                log.debug("第二次删除缓存: {}", cacheKey);
            } catch (Exception e) {
                log.warn("第二次删除失败: {}", cacheKey, e);
            }
        }, new Date(System.currentTimeMillis() + SECOND_DELETE_DELAY_MS));
    }

    private void deleteCache(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildConfigCacheKey(String configId) {
        return "llm:cfg:" + configId;
    }
}