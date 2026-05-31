package com.qingluo.link.components.redis.service;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

/**
 * 统一缓存一致性执行器。
 *
 * <p>写请求成功更新 MySQL 后，通过本类执行同步删缓存；CDC 补偿消息到达后，
 * 也复用同一套 key 路由再次删除缓存，保证项目内删缓存入口一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheKeyRouter cacheKeyRouter;
    private final CacheConsistencyProperties properties;

    /**
     * 主请求同步删缓存入口。
     */
    public void evict(CacheEvictTarget target, Object identifier) {
        if (!properties.isEnabled()) {
            log.debug("Cache consistency disabled, skip sync eviction target={}, identifier={}", target, identifier);
            return;
        }
        List<String> keys = cacheKeyRouter.route(target, String.valueOf(identifier));
        deleteKeysWithinBudget(keys, properties.isSyncDeleteRequired(), "sync");
    }

    /**
     * CDC / MQ 二次删除补偿入口。
     */
    public void evictCompensation(CacheEvictTarget target, Object identifier) {
        List<String> keys = cacheKeyRouter.route(target, String.valueOf(identifier));
        deleteKeysWithinBudget(keys, true, "compensation");
    }

    /**
     * 直接按明确 key 集合删除，供少量特殊场景复用。
     */
    public void evictDirect(Collection<String> keys) {
        deleteKeysWithinBudget(keys, true, "direct");
    }

    /**
     * 在统一时间预算内做快速重试。
     *
     * <p>主请求是否失败由 {@code alwaysThrow} 控制；补偿链路始终抛出异常，
     * 由 MQ / 消费框架负责后续重试。</p>
     */
    private void deleteKeysWithinBudget(Collection<String> keys, boolean alwaysThrow, String scene) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        long deadline = System.nanoTime() + properties.getSyncDeleteMaxWaitMs() * 1_000_000L;
        int attempt = 0;
        while (System.nanoTime() <= deadline) {
            attempt++;
            try {
                redisTemplate.delete(keys);
                log.debug("Cache delete succeeded scene={}, attempt={}, keys={}", scene, attempt, keys);
                return;
            } catch (Exception ex) {
                log.warn("Cache delete failed scene={}, attempt={}, keys={}, error={}",
                        scene, attempt, keys, ex.getMessage());
                if (System.nanoTime() > deadline) {
                    break;
                }
                sleepSilently(properties.getSyncDeleteRetryIntervalMs());
            }
        }

        if (!alwaysThrow) {
            log.warn("Cache delete budget exhausted but request keeps going, scene={}, keys={}", scene, keys);
            return;
        }
        String message = "缓存删除失败，请稍后重试";
        throw new BusinessException(ErrorCode.CACHE_DELETE_FAILED, message + "，keys=" + keys);
    }

    /**
     * 同步删缓存采用主线程快速等待，不单独引入额外调度线程。
     */
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
}
