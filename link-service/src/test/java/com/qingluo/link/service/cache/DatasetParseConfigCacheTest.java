package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.components.redis.service.CacheRoute;
import com.qingluo.link.model.dto.cache.DatasetParseConfigCacheEnvelope;
import com.qingluo.link.model.dto.cache.DatasetParseConfigSnapshot;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetParseConfigCacheTest {

    @Mock private CacheReadProtectionService readProtectionService;
    @Mock private CacheConsistencyService consistencyService;
    @Mock private CacheKeyRouter keyRouter;
    @Mock private BusinessCacheProperties properties;
    @Mock private BusinessCacheReadiness readiness;
    private DatasetParseConfigCache cache;
    private CacheRoute route;

    @BeforeEach
    void setUp() {
        cache = new DatasetParseConfigCache(
            readProtectionService, consistencyService, keyRouter, properties, readiness);
        route = CacheRoute.of("cache:dataset:parse-config:{dataset-config:10}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readyCache_wrapsRawSnapshotInVersionedEnvelope() {
        DatasetParseConfigSnapshot snapshot = snapshot(7L, 10L);
        prepareReadyCache();
        when(readProtectionService.getOrLoad(
            eq(route), eq(DatasetParseConfigCacheEnvelope.class), eq(604800L), eq(TimeUnit.SECONDS),
            any(Supplier.class), any(Predicate.class), any(Predicate.class)))
            .thenAnswer(invocation ->
                ((Supplier<DatasetParseConfigCacheEnvelope>) invocation.getArgument(4)).get());

        DatasetParseConfigSnapshot result = cache.get(7L, 10L, () -> snapshot);

        assertThat(result).isSameAs(snapshot);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingRow_usesNotFoundEnvelopeAndReturnsNull() {
        prepareReadyCache();
        when(readProtectionService.getOrLoad(
            eq(route), eq(DatasetParseConfigCacheEnvelope.class), eq(604800L), eq(TimeUnit.SECONDS),
            any(Supplier.class), any(Predicate.class), any(Predicate.class)))
            .thenAnswer(invocation ->
                ((Supplier<DatasetParseConfigCacheEnvelope>) invocation.getArgument(4)).get());

        assertThat(cache.get(7L, 10L, () -> null)).isNull();
    }

    @Test
    void cacheNotReady_bypassesRedisAndReturnsDatabaseSnapshot() {
        DatasetParseConfigSnapshot snapshot = snapshot(7L, 10L);
        when(readiness.isDatabaseMirrorCacheEnabled()).thenReturn(false);

        assertThat(cache.get(7L, 10L, () -> snapshot)).isSameAs(snapshot);
        verifyNoInteractions(readProtectionService, keyRouter, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mismatchedLoaderSnapshot_isRejectedBeforeRedisBackfill() {
        prepareReadyCache();
        when(readProtectionService.getOrLoad(
            eq(route), eq(DatasetParseConfigCacheEnvelope.class), eq(604800L), eq(TimeUnit.SECONDS),
            any(Supplier.class), any(Predicate.class), any(Predicate.class)))
            .thenAnswer(invocation ->
                ((Supplier<DatasetParseConfigCacheEnvelope>) invocation.getArgument(4)).get());

        assertThatThrownBy(() -> cache.get(7L, 10L, () -> snapshot(8L, 10L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid snapshot");
    }

    private void prepareReadyCache() {
        when(readiness.isDatabaseMirrorCacheEnabled()).thenReturn(true);
        when(keyRouter.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10")).thenReturn(route);
        when(properties.getDatasetParseConfigTtl()).thenReturn(Duration.ofDays(7));
    }

    private DatasetParseConfigSnapshot snapshot(Long userId, Long datasetId) {
        DatasetParseConfigSnapshot snapshot = new DatasetParseConfigSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDatasetId(datasetId);
        snapshot.setChunkingConfig(new ChunkingConfig());
        snapshot.setEnhancementConfig(new EnhancementConfig());
        snapshot.setPdfConfig(new PdfConfig());
        snapshot.setRecallConfig(new RecallConfig());
        snapshot.setIsActive(true);
        return snapshot;
    }
}
