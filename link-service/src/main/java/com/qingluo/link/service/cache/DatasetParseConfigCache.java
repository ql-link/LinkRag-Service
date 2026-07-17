package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
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

    public DatasetParseConfigResponse get(Long datasetId, Supplier<DatasetParseConfigResponse> loader) {
        if (!readiness.isDatabaseMirrorCacheEnabled()) {
            return loader.get();
        }
        return readProtectionService.getOrLoad(
            keyRouter.route(CacheEvictTarget.DATASET_PARSE_CONFIG, String.valueOf(datasetId)),
            DatasetParseConfigResponse.class,
            properties.getDatasetParseConfigTtl().toSeconds(),
            TimeUnit.SECONDS,
            loader);
    }

    public void evict(Long datasetId) {
        consistencyService.evict(CacheEvictTarget.DATASET_PARSE_CONFIG, datasetId);
    }
}
