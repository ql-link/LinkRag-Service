package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据库镜像缓存只有在 CDC 路由和补偿消费者就绪后才允许启用。
 */
@Component
@RequiredArgsConstructor
public class BusinessCacheReadiness {

    private final CacheConsistencyProperties consistencyProperties;
    private final BusinessCacheProperties businessCacheProperties;

    public boolean isDatabaseMirrorCacheEnabled() {
        CacheConsistencyProperties.Cdc cdc = consistencyProperties.getCdc();
        return consistencyProperties.isEnabled()
            && businessCacheProperties.isEnabled()
            && cdc.isEnabled()
            && cdc.isMappingsEnabled()
            && cdc.isConsumerTargetsReady()
            && StringUtils.hasText(cdc.getSourceTopic())
            && StringUtils.hasText(cdc.getDatabase());
    }

    public String notReadyReason() {
        CacheConsistencyProperties.Cdc cdc = consistencyProperties.getCdc();
        if (!consistencyProperties.isEnabled() || !businessCacheProperties.isEnabled()) {
            return "CACHE_DISABLED";
        }
        if (!cdc.isEnabled()
            || !StringUtils.hasText(cdc.getSourceTopic())
            || !StringUtils.hasText(cdc.getDatabase())) {
            return "CDC_SOURCE_NOT_READY";
        }
        if (!cdc.isConsumerTargetsReady()) {
            return "CONSUMER_DOES_NOT_RECOGNIZE_NEW_TARGET";
        }
        if (!cdc.isMappingsEnabled()) {
            return "CDC_MAPPING_DISABLED";
        }
        return "READY";
    }
}
