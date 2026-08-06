package com.qingluo.link.api.config;

import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.cache.LLMRuntimeCacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConfigurationProperties 绑定测试。
 *
 * <p>验证重构后各 @ConfigurationProperties 类在 local profile 下能正常绑定，
 * 确保 YAML 配置键名与 Properties 类字段的映射关系未被破坏。</p>
 *
 * <p>注意：MQProperties 仅在 vender=kafka/rabbitmq 时由自动配置注册，
 * 测试环境 vender=none 时需通过 TestConfiguration 显式启用以验证绑定。</p>
 *
 * <p>Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5</p>
 */
@SpringBootTest(properties = {
        "tolink.mq.vender=none",
        "LLM_SECRET=test-only-secret",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("local")
@DisplayName("ConfigurationProperties 绑定测试")
class ConfigurationBindingTest {

    /**
     * 显式启用 MQProperties，因为 vender=none 时 MQ 自动配置不会注册该 Bean。
     */
    @TestConfiguration
    @EnableConfigurationProperties(MQProperties.class)
    static class MQPropertiesTestConfig {
    }

    @Autowired
    private DocumentFileProperties documentFileProperties;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private MQProperties mqProperties;

    @Autowired
    private CacheConsistencyProperties cacheConsistencyProperties;

    @Autowired
    private LLMRuntimeCacheProperties llmRuntimeCacheProperties;

    @Test
    @DisplayName("DocumentFileProperties: maxSizeBytes 绑定为 20971520")
    void documentFileMaxSizeBytes() {
        assertThat(documentFileProperties.getMaxSizeBytes()).isEqualTo(20971520L);
    }

    @Test
    @DisplayName("DocumentFileProperties: allowedSuffixes 与 Python 解析契约一致")
    void documentFileAllowedSuffixes() {
        assertThat(documentFileProperties.getAllowedSuffixes())
            .containsExactly("md", "markdown", "pdf", "docx", "html", "htm");
    }

    @Test
    @DisplayName("OssProperties: serviceType 绑定为 minio")
    void ossServiceType() {
        assertThat(ossProperties.getServiceType()).isEqualTo("minio");
    }

    @Test
    @DisplayName("MQProperties: vender 绑定为 none")
    void mqVender() {
        assertThat(mqProperties.getVender()).isEqualTo("none");
    }

    @Test
    @DisplayName("CacheConsistencyProperties: syncDeleteRequired 绑定为 false")
    void cacheConsistencySyncDeleteRequired() {
        assertThat(cacheConsistencyProperties.isSyncDeleteRequired()).isFalse();
    }

    @Test
    @DisplayName("LLMRuntimeCacheProperties: 三项发布门禁默认全部关闭")
    void llmRuntimeCacheGatesDefaultToClosed() {
        assertThat(llmRuntimeCacheProperties.isEnabled()).isFalse();
        assertThat(llmRuntimeCacheProperties.isCdcMappingEnabled()).isFalse();
        assertThat(llmRuntimeCacheProperties.isConsumerTargetsReady()).isFalse();
    }
}
