package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LLMProtocolServiceImpl} 单元测试，承接 acceptance「非法协议枚举拒绝保存」。
 */
class LLMProtocolServiceImplTest {

    private final LLMProtocolServiceImpl service = new LLMProtocolServiceImpl();

    @ParameterizedTest
    @ValueSource(strings = {"openai", "anthropic", "google", "jina", "dashscope", "bge_m3", "doubao_vision"})
    @DisplayName("7 个受支持协议校验通过")
    void validateProtocol_acceptsSupported(String protocol) {
        assertThatCode(() -> service.validateProtocol(protocol)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cohere", "voyage", "dashscope_rerank", "OPENAI", "", " "})
    @DisplayName("非受支持/非规范写法协议拒绝（INVALID_PROTOCOL）")
    void validateProtocol_rejectsUnsupported(String protocol) {
        assertThatThrownBy(() -> service.validateProtocol(protocol))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_PROTOCOL.getCode());
    }

    @Test
    @DisplayName("null 协议拒绝")
    void validateProtocol_rejectsNull() {
        assertThatThrownBy(() -> service.validateProtocol(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_PROTOCOL.getCode());
    }
}
