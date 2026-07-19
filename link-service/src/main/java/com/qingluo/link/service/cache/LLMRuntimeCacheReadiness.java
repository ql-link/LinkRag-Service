package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM runtime cache 的 Java 失效 target 只有在 CDC 与新消费者同时就绪后才可启用。
 */
@Component
@RequiredArgsConstructor
public class LLMRuntimeCacheReadiness {

    public static final String CONSUMER_TARGET_NOT_READY =
        "LLM_RUNTIME_CONSUMER_TARGET_NOT_READY";

    private final CacheConsistencyProperties consistencyProperties;
    private final LLMRuntimeCacheProperties runtimeCacheProperties;

    public boolean isEnabled() {
        CacheConsistencyProperties.Cdc cdc = consistencyProperties.getCdc();
        return consistencyProperties.isEnabled()
            && runtimeCacheProperties.isEnabled()
            && cdc.isEnabled()
            && cdc.isMappingsEnabled()
            && runtimeCacheProperties.isCdcMappingEnabled()
            && runtimeCacheProperties.isConsumerTargetsReady()
            && StringUtils.hasText(cdc.getSourceTopic())
            && StringUtils.hasText(cdc.getDatabase());
    }

    public boolean isMappingEnabled() {
        return isEnabled();
    }

    public String notReadyReason() {
        CacheConsistencyProperties.Cdc cdc = consistencyProperties.getCdc();
        if (!consistencyProperties.isEnabled() || !runtimeCacheProperties.isEnabled()) {
            return "LLM_RUNTIME_CACHE_DISABLED";
        }
        if (!cdc.isEnabled()
            || !StringUtils.hasText(cdc.getSourceTopic())
            || !StringUtils.hasText(cdc.getDatabase())) {
            return "CDC_SOURCE_NOT_READY";
        }
        if (!runtimeCacheProperties.isConsumerTargetsReady()) {
            return CONSUMER_TARGET_NOT_READY;
        }
        if (!cdc.isMappingsEnabled() || !runtimeCacheProperties.isCdcMappingEnabled()) {
            return "LLM_RUNTIME_CDC_MAPPING_DISABLED";
        }
        return "READY";
    }
}
