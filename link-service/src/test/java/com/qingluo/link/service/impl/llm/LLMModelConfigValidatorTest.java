package com.qingluo.link.service.impl.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.LLMModelConfigMapper;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LLMModelConfigValidatorTest {

    @Mock
    private LLMModelConfigMapper configMapper;
    @Mock
    private LLMCapabilityService capabilityService;

    @InjectMocks
    private LLMModelConfigValidatorImpl validator;

    @Test
    void notFoundHasHighestPriority() {
        given(configMapper.selectById(10L)).willReturn(null);

        assertError(10L, ErrorCode.LLM_CONFIG_NOT_FOUND);
    }

    @Test
    void inactivePrecedesOwnerAndCapabilityChecks() {
        LLMModelConfig config = userConfig(10L, 2L, "EMBEDDING", false);
        given(configMapper.selectById(10L)).willReturn(config);

        assertError(10L, ErrorCode.LLM_CONFIG_INACTIVE);
    }

    @Test
    void forbiddenPrecedesCapabilityCheck() {
        LLMModelConfig config = userConfig(10L, 2L, "EMBEDDING", true);
        given(configMapper.selectById(10L)).willReturn(config);

        assertError(10L, ErrorCode.LLM_CONFIG_FORBIDDEN);
    }

    @Test
    void capabilityMismatchComesAfterOwnership() {
        LLMModelConfig config = userConfig(10L, 1L, "EMBEDDING", true);
        given(configMapper.selectById(10L)).willReturn(config);

        assertError(10L, ErrorCode.LLM_CONFIG_CAPABILITY_MISMATCH);
    }

    @Test
    void activeSystemConfigIsSharedAcrossAuthenticatedUsers() {
        LLMModelConfig config = userConfig(10L, 0L, "CHAT", true);
        config.setScope(LLMConfigScope.SYSTEM.name());
        given(configMapper.selectById(10L)).willReturn(config);

        assertThat(validator.requireExecutable(1L, 10L, "chat")).isSameAs(config);
    }

    private void assertError(Long configId, ErrorCode errorCode) {
        assertThatThrownBy(() -> validator.requireExecutable(1L, configId, "CHAT"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(errorCode.getCode()));
    }

    private LLMModelConfig userConfig(Long id, Long ownerId, String capability, boolean active) {
        LLMModelConfig config = new LLMModelConfig();
        config.setId(id);
        config.setScope(LLMConfigScope.USER.name());
        config.setOwnerUserId(ownerId);
        config.setCapability(capability);
        config.setIsActive(active);
        return config;
    }
}
