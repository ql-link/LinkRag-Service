package com.qingluo.link.components.redis.service;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

/**
 * 统一缓存一致性执行器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private static final Object DEFERRED_FIRST_DELETE_RESOURCE_KEY = new Object();

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheKeyRouter cacheKeyRouter;
    private final CacheConsistencyProperties properties;
    private final CacheAtomicOperations atomicOperations;
    private final CacheMetrics metrics;

    public void evict(CacheEvictTarget target, Object identifier) {
        if (!properties.isEnabled()) {
            log.debug("Cache consistency disabled, skip sync eviction target={}, identifier={}", target, identifier);
            return;
        }
        collectOrDeleteNow(cacheKeyRouter.route(target, String.valueOf(identifier)));
    }

    public void evictCompensation(CacheEvictTarget target, Object identifier) {
        invalidateWithinBudget(
            List.of(cacheKeyRouter.route(target, String.valueOf(identifier))),
            true,
            "compensation");
    }

    public void evictDirect(Collection<String> keys) {
        deleteKeysWithinBudget(keys, true, "direct");
    }

    private void collectOrDeleteNow(CacheRoute route) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateWithinBudget(List.of(route), false, "sync-no-tx");
            return;
        }

        DeferredFirstDeleteHolder holder = getOrCreateDeferredFirstDeleteHolder();
        holder.routes.add(route);
        registerAfterCommitIfNeeded(holder);
    }

    private DeferredFirstDeleteHolder getOrCreateDeferredFirstDeleteHolder() {
        Object resource = TransactionSynchronizationManager.getResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        if (resource instanceof DeferredFirstDeleteHolder holder) {
            return holder;
        }
        DeferredFirstDeleteHolder holder = new DeferredFirstDeleteHolder();
        TransactionSynchronizationManager.bindResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY, holder);
        return holder;
    }

    private void registerAfterCommitIfNeeded(DeferredFirstDeleteHolder holder) {
        if (holder.synchronizationRegistered) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void suspend() {
                unbindHolderIfCurrent(holder);
            }

            @Override
            public void resume() {
                if (!TransactionSynchronizationManager.hasResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.bindResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY, holder);
                }
            }

            @Override
            public void afterCommit() {
                flushDeferredFirstDelete(holder);
            }

            @Override
            public void afterCompletion(int status) {
                unbindHolderIfCurrent(holder);
            }
        });
        holder.synchronizationRegistered = true;
    }

    private void flushDeferredFirstDelete(DeferredFirstDeleteHolder holder) {
        if (!holder.routes.isEmpty()) {
            invalidateWithinBudget(new ArrayList<>(holder.routes), false, "sync-after-commit");
        }
    }

    private void unbindHolderIfCurrent(DeferredFirstDeleteHolder holder) {
        Object resource = TransactionSynchronizationManager.getResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        if (resource == holder) {
            TransactionSynchronizationManager.unbindResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        }
    }

    private void invalidateWithinBudget(Collection<CacheRoute> routes, boolean alwaysThrow, String scene) {
        if (CollectionUtils.isEmpty(routes)) {
            return;
        }
        long deadline = System.nanoTime() + properties.getSyncDeleteMaxWaitMs() * 1_000_000L;
        int attempt = 0;
        while (System.nanoTime() <= deadline) {
            attempt++;
            try {
                for (CacheRoute route : routes) {
                    atomicOperations.invalidate(route);
                }
                recordDeleteMetric(scene, "success");
                return;
            } catch (Exception ex) {
                log.warn("Cache invalidate failed scene={}, attempt={}, routes={}, error={}",
                    scene, attempt, routes, ex.getMessage());
                if (System.nanoTime() > deadline) {
                    break;
                }
                sleepSilently(properties.getSyncDeleteRetryIntervalMs());
            }
        }

        recordDeleteMetric(scene, "error");
        if (!alwaysThrow) {
            log.warn("Cache invalidate budget exhausted but request keeps going, scene={}, routes={}", scene, routes);
            return;
        }
        throw new BusinessException(ErrorCode.CACHE_DELETE_FAILED, "缓存删除失败，请稍后重试，routes=" + routes);
    }

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
            return;
        }
        throw new BusinessException(ErrorCode.CACHE_DELETE_FAILED, "缓存删除失败，请稍后重试，keys=" + keys);
    }

    private void recordDeleteMetric(String scene, String outcome) {
        if ("compensation".equals(scene)) {
            metrics.compensationDelete(outcome);
        } else {
            metrics.firstDelete(outcome);
        }
    }

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

    private static final class DeferredFirstDeleteHolder {

        private final LinkedHashSet<CacheRoute> routes = new LinkedHashSet<>();
        private boolean synchronizationRegistered;
    }
}
