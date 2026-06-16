package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link CacheReadProtectionService#getOrLoadBatch} 单元测试。
 *
 * <p>覆盖批量读保护：全命中不回源、部分缺失回源回填、空值占位跳过、回源无值写空值占位防穿透，
 * 以及读/回填故障分别处理——MGET 读故障向上抛出（由调用方降级），回填故障吞掉不影响已加载值返回。</p>
 */
@ExtendWith(MockitoExtension.class)
class CacheReadProtectionServiceBatchTest {

    private static final String NULL_MARKER = "__NULL__";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private CacheConsistencyProperties properties;

    @InjectMocks
    private CacheReadProtectionService service;

    @Test
    @DisplayName("全命中：直接返回，不触发回源")
    void getOrLoadBatch_allHit_noLoader() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(List.of("k1", "k2"))).willReturn(Arrays.<Object>asList("v1", "v2"));

        Map<String, String> result = service.getOrLoadBatch(List.of("k1", "k2"), String.class, 60, TimeUnit.MINUTES,
                missing -> {
                    throw new AssertionError("loader 不应被调用");
                });

        assertThat(result).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    @DisplayName("部分缺失：仅缺失 key 回源并回填真实值")
    void getOrLoadBatch_partialMiss_loadsAndBackfills() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(List.of("k1", "k2"))).willReturn(Arrays.<Object>asList("v1", null)); // k2 缺失
        given(properties.getTtlJitterSeconds()).willReturn(0L);

        Map<String, String> result = service.getOrLoadBatch(List.of("k1", "k2"), String.class, 60, TimeUnit.MINUTES,
                missing -> Map.of("k2", "v2"));

        assertThat(result).containsEntry("k1", "v1").containsEntry("k2", "v2");
        verify(valueOps).set(eq("k2"), eq("v2"), any(Duration.class));
    }

    @Test
    @DisplayName("空值占位命中：既不缺失也不计入结果")
    void getOrLoadBatch_nullMarkerHit_skipped() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(List.of("k1"))).willReturn(Arrays.<Object>asList(NULL_MARKER));

        Map<String, String> result = service.getOrLoadBatch(List.of("k1"), String.class, 60, TimeUnit.MINUTES,
                missing -> {
                    throw new AssertionError("loader 不应被调用");
                });

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("回源无值：写空值占位防穿透，不计入结果")
    void getOrLoadBatch_loaderReturnsNothing_writesNullPlaceholder() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(List.of("k1"))).willReturn(Arrays.asList((Object) null));
        given(properties.getNullCacheTtlSeconds()).willReturn(60L);

        Map<String, String> result = service.getOrLoadBatch(List.of("k1"), String.class, 60, TimeUnit.MINUTES,
                missing -> Map.of());

        assertThat(result).isEmpty();
        verify(valueOps).set(eq("k1"), eq(NULL_MARKER), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("读故障：MGET 抛异常向上抛出（交由调用方降级）")
    void getOrLoadBatch_readFailure_propagates() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(any())).willThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.getOrLoadBatch(List.of("k1"), String.class, 60, TimeUnit.MINUTES,
                missing -> Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");
    }

    @Test
    @DisplayName("回填故障：写 Redis 抛异常被吞掉，仍返回已加载值")
    void getOrLoadBatch_backfillFailure_swallowed_returnsLoaded() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.multiGet(List.of("k1"))).willReturn(Arrays.asList((Object) null));
        given(properties.getTtlJitterSeconds()).willReturn(0L);
        willThrow(new RuntimeException("write timeout")).given(valueOps).set(eq("k1"), eq("v1"), any(Duration.class));

        Map<String, String> result = service.getOrLoadBatch(List.of("k1"), String.class, 60, TimeUnit.MINUTES,
                missing -> Map.of("k1", "v1"));

        assertThat(result).containsEntry("k1", "v1");
    }
}
