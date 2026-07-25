package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessCacheReadinessTest {

    private CacheConsistencyProperties consistencyProperties;
    private BusinessCacheProperties businessCacheProperties;
    private BusinessCacheReadiness readiness;

    @BeforeEach
    void setUp() {
        consistencyProperties = new CacheConsistencyProperties();
        businessCacheProperties = new BusinessCacheProperties();
        readiness = new BusinessCacheReadiness(consistencyProperties, businessCacheProperties);
    }

    @Test
    void allConsistencyCapabilitiesReady_enablesDatabaseMirrorCaches() {
        consistencyProperties.setEnabled(true);
        consistencyProperties.getCdc().setEnabled(true);
        consistencyProperties.getCdc().setMappingsEnabled(true);
        consistencyProperties.getCdc().setConsumerTargetsReady(true);
        consistencyProperties.getCdc().setDatabase("tolink_rag_db");
        consistencyProperties.getCdc().setSourceTopic("tolink.canal.binlog");

        assertThat(readiness.isDatabaseMirrorCacheEnabled()).isTrue();
        assertThat(readiness.notReadyReason()).isEqualTo("READY");
    }

    @Test
    void mappingNotEnabled_keepsDatabaseMirrorCachesDisabled() {
        consistencyProperties.setEnabled(true);
        consistencyProperties.getCdc().setEnabled(true);
        consistencyProperties.getCdc().setMappingsEnabled(false);
        consistencyProperties.getCdc().setConsumerTargetsReady(true);
        consistencyProperties.getCdc().setDatabase("tolink_rag_db");
        consistencyProperties.getCdc().setSourceTopic("tolink.canal.binlog");

        assertThat(readiness.isDatabaseMirrorCacheEnabled()).isFalse();
        assertThat(readiness.notReadyReason()).isEqualTo("CDC_MAPPING_DISABLED");
    }

    @Test
    void consumerNotReady_hasPriorityOverMappingSwitch() {
        consistencyProperties.setEnabled(true);
        consistencyProperties.getCdc().setEnabled(true);
        consistencyProperties.getCdc().setMappingsEnabled(false);
        consistencyProperties.getCdc().setConsumerTargetsReady(false);
        consistencyProperties.getCdc().setSourceTopic("tolink.canal.binlog");

        assertThat(readiness.isDatabaseMirrorCacheEnabled()).isFalse();
        assertThat(readiness.notReadyReason()).isEqualTo("CONSUMER_DOES_NOT_RECOGNIZE_NEW_TARGET");
    }
}
