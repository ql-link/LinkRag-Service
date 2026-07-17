package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LLMRuntimeCacheReadinessTest {

    private CacheConsistencyProperties consistencyProperties;
    private LLMRuntimeCacheProperties runtimeCacheProperties;
    private LLMRuntimeCacheReadiness readiness;

    @BeforeEach
    void setUp() {
        consistencyProperties = new CacheConsistencyProperties();
        runtimeCacheProperties = new LLMRuntimeCacheProperties();
        readiness = new LLMRuntimeCacheReadiness(consistencyProperties, runtimeCacheProperties);

        consistencyProperties.setEnabled(true);
        consistencyProperties.getCdc().setEnabled(true);
        consistencyProperties.getCdc().setMappingsEnabled(true);
        consistencyProperties.getCdc().setDatabase("tolink_rag_db");
        consistencyProperties.getCdc().setSourceTopic("tolink.canal.binlog");
        runtimeCacheProperties.setEnabled(true);
        runtimeCacheProperties.setCdcMappingEnabled(true);
        runtimeCacheProperties.setConsumerTargetsReady(true);
    }

    @Test
    void allCapabilitiesReady_enablesLlmRuntimeMapping() {
        assertThat(readiness.isEnabled()).isTrue();
        assertThat(readiness.isMappingEnabled()).isTrue();
        assertThat(readiness.notReadyReason()).isEqualTo("READY");
    }

    @Test
    void consumerTargetGate_hasStableReasonAndPriority() {
        runtimeCacheProperties.setConsumerTargetsReady(false);
        runtimeCacheProperties.setCdcMappingEnabled(false);

        assertThat(readiness.isEnabled()).isFalse();
        assertThat(readiness.notReadyReason())
            .isEqualTo("LLM_RUNTIME_CONSUMER_TARGET_NOT_READY");
    }

    @Test
    void businessCacheSwitch_doesNotControlLlmReadiness() {
        BusinessCacheProperties businessCacheProperties = new BusinessCacheProperties();
        businessCacheProperties.setEnabled(false);

        assertThat(readiness.isEnabled()).isTrue();
    }

    @Test
    void cdcSourceAndMappingAreRequired() {
        consistencyProperties.getCdc().setSourceTopic(null);
        assertThat(readiness.notReadyReason()).isEqualTo("CDC_SOURCE_NOT_READY");

        consistencyProperties.getCdc().setSourceTopic("tolink.canal.binlog");
        runtimeCacheProperties.setCdcMappingEnabled(false);
        assertThat(readiness.notReadyReason()).isEqualTo("LLM_RUNTIME_CDC_MAPPING_DISABLED");
    }
}
