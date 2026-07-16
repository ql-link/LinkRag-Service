package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheConsistencyServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    private CacheConsistencyService service;

    @BeforeEach
    void setUp() {
        CacheConsistencyProperties properties = new CacheConsistencyProperties();
        properties.setEnabled(true);
        properties.setSyncDeleteMaxWaitMs(1L);
        properties.setSyncDeleteRetryIntervalMs(0L);
        service = new CacheConsistencyService(redisTemplate, new CacheKeyRouter(), properties);
    }

    @Test
    void businessTargetRouting_isEmpty() {
        service.evict(null, 1L);
        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void directEviction_baseCapabilityRemains() {
        service.evictDirect(List.of("future:key"));
        verify(redisTemplate).delete(List.of("future:key"));
    }

    @Test
    void directEviction_failureStillRaisesBusinessException() {
        when(redisTemplate.delete(List.of("future:key"))).thenThrow(new RuntimeException("redis down"));
        assertThatThrownBy(() -> service.evictDirect(List.of("future:key")))
            .isInstanceOf(BusinessException.class);
    }
}
