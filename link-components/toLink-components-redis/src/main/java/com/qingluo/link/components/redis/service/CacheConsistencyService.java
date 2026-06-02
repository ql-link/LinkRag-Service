package com.qingluo.link.components.redis.service;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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

    private static final Object DEFERRED_FIRST_DELETE_RESOURCE_KEY = new Object();

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheKeyRouter cacheKeyRouter;
    private final CacheConsistencyProperties properties;

    /**
     * 主流程第一次删缓存入口。
     *
     * <p>调用前提：业务侧已经完成并认可数据库写成功。该方法不负责判断
     * “数据库是否应该算成功”，只负责在这个前提下按事务状态安排首删时机。</p>
     */
    public void evict(CacheEvictTarget target, Object identifier) {
        if (!properties.isEnabled()) {
            log.debug("Cache consistency disabled, skip sync eviction target={}, identifier={}", target, identifier);
            return;
        }
        List<String> keys = cacheKeyRouter.route(target, String.valueOf(identifier));
        collectOrDeleteNow(keys);
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
     * 第一次删缓存的统一分发入口：
     * 无事务时立即删；有事务时先挂到当前事务，等 afterCommit 再删。
     */
    private void collectOrDeleteNow(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteKeysWithinBudget(keys, false, "sync-no-tx");
            return;
        }

        DeferredFirstDeleteHolder holder = getOrCreateDeferredFirstDeleteHolder();
        holder.keys.addAll(keys);
        registerAfterCommitIfNeeded(holder);
    }

    /**
     * 复用当前事务里的待删 key 容器；首次进入事务时才创建并绑定。
     */
    private DeferredFirstDeleteHolder getOrCreateDeferredFirstDeleteHolder() {
        Object resource = TransactionSynchronizationManager.getResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        if (resource instanceof DeferredFirstDeleteHolder holder) {
            return holder;
        }
        DeferredFirstDeleteHolder holder = new DeferredFirstDeleteHolder();
        TransactionSynchronizationManager.bindResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY, holder);
        return holder;
    }

    /**
     * 每个事务只注册一次同步回调，避免同一事务内重复删同一批 key。
     */
    private void registerAfterCommitIfNeeded(DeferredFirstDeleteHolder holder) {
        if (holder.synchronizationRegistered) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void suspend() {
                // REQUIRES_NEW 挂起外层事务时，临时解绑外层 holder，避免污染内层事务。
                unbindHolderIfCurrent(holder);
            }

            @Override
            public void resume() {
                // 外层事务恢复时，再把原 holder 绑回当前线程。
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

    /**
     * 事务提交后统一删除当前事务聚合出的全部 key。
     */
    private void flushDeferredFirstDelete(DeferredFirstDeleteHolder holder) {
        if (holder.keys.isEmpty()) {
            return;
        }
        deleteKeysWithinBudget(new ArrayList<>(holder.keys), false, "sync-after-commit");
    }

    /**
     * 只在当前绑定资源就是目标 holder 时才解绑，避免误清理其他事务上下文。
     */
    private void unbindHolderIfCurrent(DeferredFirstDeleteHolder holder) {
        Object resource = TransactionSynchronizationManager.getResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        if (resource == holder) {
            TransactionSynchronizationManager.unbindResource(DEFERRED_FIRST_DELETE_RESOURCE_KEY);
        }
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

    /**
     * 单事务内的待删 key 聚合器：
     * 去重 key，并记录该事务是否已经注册过同步回调。
     */
    private static final class DeferredFirstDeleteHolder {

        private final LinkedHashSet<String> keys = new LinkedHashSet<>();
        private boolean synchronizationRegistered;
    }
}
