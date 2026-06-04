package com.qingluo.link.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LLMCapabilityServiceImpl} 单元测试。
 *
 * <p>回归点：种子数据 {@code llm_system_provider.supported_capabilities} 中使用的能力词汇
 * （含 {@code TOOL_CALLING} / {@code RERANK}）必须全部落在
 * {@code SUPPORTED_CAPABILITIES} 白名单内，否则 {@code GET /api/v1/llm/providers}
 * 在解析厂商能力时会抛 {@code INVALID_MODEL_CAPABILITY(10011)}。</p>
 */
class LLMCapabilityServiceImplTest {

    private final LLMCapabilityServiceImpl service = new LLMCapabilityServiceImpl(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"CHAT", "EMBEDDING", "OCR", "VISION", "REASONING", "CODE", "TOOL_CALLING", "RERANK"})
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
    void parseSupportedCapabilities_parsesSeedLikeProviderWithToolCallingAndRerank() {
        String supportedCapabilities = "[\"CHAT\",\"TOOL_CALLING\",\"VISION\",\"OCR\",\"RERANK\",\"EMBEDDING\"]";

        List<String> parsed = service.parseSupportedCapabilities(supportedCapabilities);

        assertThat(parsed).containsExactly("CHAT", "TOOL_CALLING", "VISION", "OCR", "RERANK", "EMBEDDING");
    }
}
