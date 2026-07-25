package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.cache.DatasetParseConfigCacheEnvelope;
import com.qingluo.link.model.dto.cache.DatasetParseConfigSnapshot;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatasetParseConfigCache {

    private final CacheReadProtectionService readProtectionService;
    private final CacheConsistencyService consistencyService;
    private final CacheKeyRouter keyRouter;
    private final BusinessCacheProperties properties;
    private final BusinessCacheReadiness readiness;

    public DatasetParseConfigSnapshot get(Long userId, Long datasetId,
                                          Supplier<DatasetParseConfigSnapshot> loader) {
        if (!readiness.isDatabaseMirrorCacheEnabled()) {
            return loader.get();
        }
        DatasetParseConfigCacheEnvelope envelope = readProtectionService.getOrLoad(
            keyRouter.route(CacheEvictTarget.DATASET_PARSE_CONFIG, String.valueOf(datasetId)),
            DatasetParseConfigCacheEnvelope.class,
            properties.getDatasetParseConfigTtl().toSeconds(),
            TimeUnit.SECONDS,
            () -> envelopeOf(userId, datasetId, loader.get()),
            value -> value != null && value.isValidFor(userId, datasetId),
            value -> value != null && !value.isFound());
        return envelope.isFound() ? envelope.getValue() : null;
    }

    public void evict(Long datasetId) {
        consistencyService.evict(CacheEvictTarget.DATASET_PARSE_CONFIG, datasetId);
    }

    private DatasetParseConfigCacheEnvelope envelopeOf(Long userId, Long datasetId,
                                                        DatasetParseConfigSnapshot snapshot) {
        if (snapshot == null) {
            return DatasetParseConfigCacheEnvelope.notFound();
        }
        DatasetParseConfigCacheEnvelope envelope = DatasetParseConfigCacheEnvelope.found(snapshot);
        if (!envelope.isValidFor(userId, datasetId)) {
            throw new IllegalStateException("Dataset cache loader returned an invalid snapshot");
        }
        return envelope;
    }
}
