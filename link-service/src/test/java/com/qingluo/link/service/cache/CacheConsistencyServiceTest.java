package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.components.redis.service.CacheAtomicOperations;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheMetrics;
import com.qingluo.link.components.redis.service.CacheRoute;
import com.qingluo.link.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CacheConsistencyServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private CacheAtomicOperations atomicOperations;
    @Mock private CacheMetrics metrics;
    private CacheKeyRouter router;
    private CacheConsistencyService service;

    @BeforeEach
    void setUp() {
        CacheConsistencyProperties properties = new CacheConsistencyProperties();
        properties.setEnabled(true);
        properties.setSyncDeleteMaxWaitMs(1L);
        properties.setSyncDeleteRetryIntervalMs(0L);
        router = new CacheKeyRouter();
        service = new CacheConsistencyService(redisTemplate, router, properties, atomicOperations, metrics);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        new ArrayList<>(TransactionSynchronizationManager.getResourceMap().keySet()).forEach(key -> {
            if (TransactionSynchronizationManager.hasResource(key)) {
                TransactionSynchronizationManager.unbindResource(key);
            }
        });
    }

    @Test
    void businessTargetRouting_invalidatesFenceAndDataKey() {
        CacheRoute route = router.route(CacheEvictTarget.USER_PROFILE, "1");
        service.evict(CacheEvictTarget.USER_PROFILE, 1L);
        verify(atomicOperations).invalidate(route);
        verify(metrics).firstDelete("success");
    }

    @Test
    void directEviction_baseCapabilityRemains() {
        service.evictDirect(List.of("future:key"));
        verify(redisTemplate).delete(List.of("future:key"));
    }

    @Test
    void compensationFailureStillRaisesBusinessException() {
        CacheRoute route = router.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10");
        doThrow(new RuntimeException("redis down")).when(atomicOperations).invalidate(route);
        assertThatThrownBy(() ->
            service.evictCompensation(CacheEvictTarget.DATASET_PARSE_CONFIG, 10L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void transactionCommit_defersAndDeduplicatesFirstDelete() {
        CacheRoute route = router.route(CacheEvictTarget.USER_PROFILE, "20");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.evict(CacheEvictTarget.USER_PROFILE, 20L);
        service.evict(CacheEvictTarget.USER_PROFILE, 20L);

        verify(atomicOperations, never()).invalidate(route);
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(atomicOperations).invalidate(route);
        verify(metrics).firstDelete("success");
    }

    @Test
    void transactionRollback_doesNotDeleteCache() {
        CacheRoute route = router.route(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.evict(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global");

        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(atomicOperations, never()).invalidate(route);
    }

    @Test
    void transactionCommit_deduplicatesLlmRuntimeInvalidationByGlobalConfigId() {
        CacheRoute route = router.route(CacheEvictTarget.LLM_RUNTIME_CONFIG, "10001");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.evict(CacheEvictTarget.LLM_RUNTIME_CONFIG, 10001L);
        service.evict(CacheEvictTarget.LLM_RUNTIME_CONFIG, 10001L);

        verify(atomicOperations, never()).invalidate(route);
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync ->
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(atomicOperations).invalidate(route);
    }

    @Test
    void transactionCommit_deduplicatesSharedDatasetSnapshotInvalidation() {
        CacheRoute route = router.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.evict(CacheEvictTarget.DATASET_PARSE_CONFIG, 10L);
        service.evict(CacheEvictTarget.DATASET_PARSE_CONFIG, 10L);

        verify(atomicOperations, never()).invalidate(route);
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync ->
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(atomicOperations).invalidate(route);
    }
}
