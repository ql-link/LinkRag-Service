package com.qingluo.link.components.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 双删延迟缓存服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoubleDeleteCacheService {

    private static final int FIRST_DELETE_MAX_RETRIES = 3;
    private static final long FIRST_DELETE_RETRY_INTERVAL_MS = 200;
    private static final long SECOND_DELETE_DELAY_MS = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskScheduler taskScheduler;

    /**
     * 驱逐配置缓存（双删策略）
     */
    public void evictConfigCache(String configId) {
        String cacheKey = "llm:cfg:" + configId;
        firstDelete(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐用户默认配置缓存
     */
    public void evictDefaultConfigCache(String userId) {
        String cacheKey = "llm:u_def:" + userId;
        firstDelete(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐系统厂商缓存
     */
    public void evictProviderCache(String providerType) {
        String cacheKey = "llm:pvd:" + providerType;
        firstDelete(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 第一删（同步，带重试）
     */
    private void firstDelete(String cacheKey) {
        for (int i = 0; i < FIRST_DELETE_MAX_RETRIES; i++) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.delete(cacheKey))) {
                    log.debug("第一删成功: {}", cacheKey);
                    return;
                }
                return;
            } catch (Exception e) {
                log.warn("第一删第{}次失败: {}, error: {}", i + 1, cacheKey, e.getMessage());
                if (i < FIRST_DELETE_MAX_RETRIES - 1) {
                    sleepSilently(FIRST_DELETE_RETRY_INTERVAL_MS);
                }
            }
        }
        log.warn("第一删最终失败，依赖第二删兜底: {}", cacheKey);
    }

    /**
     * 延迟第二删（1秒后）
     */
    private void scheduleSecondDelete(String cacheKey) {
        taskScheduler.schedule(() -> {
            try {
                redisTemplate.delete(cacheKey);
                log.debug("第二次删除缓存: {}", cacheKey);
            } catch (Exception e) {
                log.warn("第二次删除失败: {}", cacheKey, e);
            }
        }, new Date(System.currentTimeMillis() + SECOND_DELETE_DELAY_MS));
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
