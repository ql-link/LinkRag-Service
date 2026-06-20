package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LLMCapabilityServiceImpl} 单元测试。
 *
 * <p>模型能力目录已迁至 llm_provider_model 表，本服务只剩能力词汇校验。
 * 回归点：种子数据使用的能力词汇必须全部落在 SUPPORTED_CAPABILITIES 白名单内，
 * 否则按能力查询/选生效会抛 INVALID_MODEL_CAPABILITY(10011)。</p>
 * <p>当前 6 个能力维度：
 * CHAT / EMBEDDING / SPARSE_EMBEDDING / VISION / RERANK / ASR。</p>
 */
class LLMCapabilityServiceImplTest {

    private final LLMCapabilityServiceImpl service = new LLMCapabilityServiceImpl();

    @ParameterizedTest
    @ValueSource(strings = {"CHAT", "EMBEDDING", "SPARSE_EMBEDDING", "VISION", "RERANK", "ASR"})
    void validateCapability_acceptsAllSeedVocabulary(String capability) {
        assertThatCode(() -> service.validateCapability(capability)).doesNotThrowAnyException();
        // 兼容大小写归一化
        assertThatCode(() -> service.validateCapability(capability.toLowerCase())).doesNotThrowAnyException();
    }

    @Test
    void validateCapability_rejectsUnknownValue() {
        assertThatThrownBy(() -> service.validateCapability("UNKNOWN_CAP"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateCapability_rejectsBlank() {
        assertThatThrownBy(() -> service.validateCapability(" "))
                .isInstanceOf(BusinessException.class);
    }
}
