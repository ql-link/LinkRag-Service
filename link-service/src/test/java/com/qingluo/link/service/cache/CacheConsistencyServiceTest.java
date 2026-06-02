package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheConsistencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Spy
    private CacheKeyRouter cacheKeyRouter;

    private CacheConsistencyService cacheConsistencyService;

    private final CacheConsistencyProperties properties = buildProperties();

    @BeforeEach
    void setUp() {
        cacheConsistencyService = new CacheConsistencyService(redisTemplate, cacheKeyRouter, properties);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("Should_DeleteImmediately_When_NoTransaction")
    void Should_DeleteImmediately_When_NoTransaction() {
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");

        verify(redisTemplate).delete(List.of("llm:pvd:openai"));
    }

    @Test
    @DisplayName("Should_NotThrow_When_FirstDeleteFails_NoTransaction")
    void Should_NotThrow_When_FirstDeleteFails_NoTransaction() {
        when(redisTemplate.delete(List.of("llm:pvd:openai"))).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should_DeferFirstDelete_UntilAfterCommit")
    void Should_DeferFirstDelete_UntilAfterCommit() {
        beginTransaction();

        cacheConsistencyService.evict(CacheEvictTarget.USER, 1L);

        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyCollection());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commitTransaction();

        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder("user:info:1", "user:role:1");
    }

    @Test
    @DisplayName("Should_NotDelete_When_TransactionRolledBack")
    void Should_NotDelete_When_TransactionRolledBack() {
        beginTransaction();

        cacheConsistencyService.evict(CacheEvictTarget.USER, 1L);
        rollbackTransaction();

        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    @DisplayName("Should_DeduplicateKeys_WithinSameTransaction")
    void Should_DeduplicateKeys_WithinSameTransaction() {
        beginTransaction();

        cacheConsistencyService.evict(CacheEvictTarget.USER, 1L);
        cacheConsistencyService.evict(CacheEvictTarget.USER_INFO, 1L);
        cacheConsistencyService.evict(CacheEvictTarget.USER_ROLE, 1L);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        commitTransaction();

        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate, times(1)).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder("user:info:1", "user:role:1");
    }

    @Test
    @DisplayName("Should_NotThrow_When_FirstDeleteFails_AfterCommit")
    void Should_NotThrow_When_FirstDeleteFails_AfterCommit() {
        beginTransaction();
        when(redisTemplate.delete(List.of("user:info:1", "user:role:1")))
                .thenThrow(new RuntimeException("redis down"));

        cacheConsistencyService.evict(CacheEvictTarget.USER, 1L);

        assertThatCode(this::commitTransaction).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should_KeepCompensationDeleteBehavior")
    void Should_KeepCompensationDeleteBehavior() {
        when(redisTemplate.delete(List.of("llm:pvd:openai"))).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> cacheConsistencyService.evictCompensation(CacheEvictTarget.SYSTEM_PROVIDER, "openai"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缓存删除失败");
    }

    private CacheConsistencyProperties buildProperties() {
        CacheConsistencyProperties value = new CacheConsistencyProperties();
        value.setEnabled(true);
        value.setSyncDeleteRequired(true);
        value.setSyncDeleteMaxWaitMs(1L);
        value.setSyncDeleteRetryIntervalMs(0L);
        return value;
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void commitTransaction() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clear();
    }

    private void rollbackTransaction() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clear();
    }
}
