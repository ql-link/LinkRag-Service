package com.qingluo.link.service.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 侧 LLM runtime cache 失效链路的独立发布门禁。
 */
@Component
@ConfigurationProperties(prefix = "tolink.llm-runtime-cache")
public class LLMRuntimeCacheProperties {

    private boolean enabled;
    private boolean cdcMappingEnabled;
    private boolean consumerTargetsReady;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCdcMappingEnabled() {
        return cdcMappingEnabled;
    }

    public void setCdcMappingEnabled(boolean cdcMappingEnabled) {
        this.cdcMappingEnabled = cdcMappingEnabled;
    }

    public boolean isConsumerTargetsReady() {
        return consumerTargetsReady;
    }

    public void setConsumerTargetsReady(boolean consumerTargetsReady) {
        this.consumerTargetsReady = consumerTargetsReady;
    }
}
