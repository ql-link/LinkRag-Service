package com.qingluo.link.service.impl.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.model.dto.entity.LLMCapabilityDefault;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LLMCapabilityDefaultServiceImplTest {

    @Mock
    private LLMCapabilityDefaultMapper defaultMapper;
    @Mock
    private LLMModelConfigValidator configValidator;
    @Mock
    private LLMCapabilityService capabilityService;

    @InjectMocks
    private LLMCapabilityDefaultServiceImpl service;

    @Test
    void setUserDefaultCreatesUniqueOverrideAndReturnsItAsEffective() {
        LLMModelConfig config = config(101L, LLMConfigScope.USER, 7L, "CHAT");
        LLMModelConfig system = config(100L, LLMConfigScope.SYSTEM, 0L, "CHAT");
        given(configValidator.requireExecutable(7L, 101L, "CHAT")).willReturn(config);
        given(configValidator.requireExecutable(0L, 100L, "CHAT")).willReturn(system);
        given(defaultMapper.selectOne(any()))
            .willReturn(null, defaultRow(LLMConfigScope.USER, 7L, "CHAT", 101L),
                defaultRow(LLMConfigScope.SYSTEM, 0L, "CHAT", 100L));

        CapabilityDefaultDTO result = service.setUserDefault(7L, "chat", 101L);

        assertThat(result.getUserDefaultConfigId()).isEqualTo(101L);
        assertThat(result.getSystemDefaultConfigId()).isEqualTo(100L);
        assertThat(result.getEffectiveConfigId()).isEqualTo(101L);
        verify(defaultMapper).insert(any(LLMCapabilityDefault.class));
    }

    @Test
    void setUserDefaultAcceptsAVisibleSystemConfig() {
        LLMModelConfig system = config(100L, LLMConfigScope.SYSTEM, 0L, "CHAT");
        given(configValidator.requireExecutable(7L, 100L, "CHAT")).willReturn(system);
        given(defaultMapper.selectOne(any()))
            .willReturn(null, defaultRow(LLMConfigScope.USER, 7L, "CHAT", 100L), null);

        CapabilityDefaultDTO result = service.setUserDefault(7L, "chat", 100L);

        assertThat(result.getUserDefaultConfigId()).isEqualTo(100L);
        assertThat(result.getSystemDefaultConfigId()).isNull();
        assertThat(result.getEffectiveConfigId()).isEqualTo(100L);
        verify(defaultMapper).insert(any(LLMCapabilityDefault.class));
    }

    @Test
    void userOverrideDoesNotHideAnInvalidSystemDefaultPointer() {
        LLMModelConfig user = config(101L, LLMConfigScope.USER, 7L, "CHAT");
        given(defaultMapper.selectOne(any())).willReturn(
            defaultRow(LLMConfigScope.USER, 7L, "CHAT", 101L),
            defaultRow(LLMConfigScope.SYSTEM, 0L, "CHAT", 100L));
        given(configValidator.requireExecutable(7L, 101L, "CHAT")).willReturn(user);
        given(configValidator.requireExecutable(0L, 100L, "CHAT"))
            .willThrow(new BusinessException(ErrorCode.LLM_CONFIG_INACTIVE));

        assertThatThrownBy(() -> service.getEffectiveDefault(7L, "CHAT"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(ErrorCode.LLM_CONFIG_INACTIVE.getCode()));
    }

    @Test
    void clearUserDefaultFallsBackToSystemWithoutChangingConfigState() {
        LLMModelConfig system = config(100L, LLMConfigScope.SYSTEM, 0L, "CHAT");
        given(defaultMapper.selectOne(any()))
            .willReturn(null, defaultRow(LLMConfigScope.SYSTEM, 0L, "CHAT", 100L));
        given(configValidator.requireExecutable(0L, 100L, "CHAT")).willReturn(system);

        CapabilityDefaultDTO result = service.clearUserDefault(7L, "CHAT");

        assertThat(result.getUserDefaultConfigId()).isNull();
        assertThat(result.getEffectiveConfigId()).isEqualTo(100L);
        verify(defaultMapper).delete(any());
    }

    @Test
    void clearSystemDefaultOnlyDeletesTheMatchingConfigPointer() {
        given(defaultMapper.selectOne(any())).willReturn(null);

        CapabilityDefaultDTO result = service.clearSystemDefaultForConfig("chat", 100L);

        assertThat(result.getSystemDefaultConfigId()).isNull();
        assertThat(result.getEffectiveConfigId()).isNull();
        verify(defaultMapper).delete(any());
    }

    @Test
    void clearSystemDefaultByCapabilityLeavesItUnconfigured() {
        given(defaultMapper.selectOne(any())).willReturn(null);

        CapabilityDefaultDTO result = service.clearSystemDefault("chat");

        assertThat(result.getSystemDefaultConfigId()).isNull();
        assertThat(result.getEffectiveConfigId()).isNull();
        verify(defaultMapper).delete(any());
    }

    @Test
    void clearUserDefaultsForSystemConfigDeletesEveryUserPointerToIt() {
        service.clearUserDefaultsForConfig(100L);

        verify(defaultMapper).delete(any());
    }

    @Test
    void requiredEffectiveDefaultFailsWhenNeitherPointerExists() {
        given(defaultMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.getEffectiveDefault(7L, "CHAT"))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(ErrorCode.LLM_DEFAULT_NOT_CONFIGURED.getCode()));
    }

    @Test
    void systemDefaultWriteFailureUsesStableDomainError() {
        LLMModelConfig system = config(100L, LLMConfigScope.SYSTEM, 0L, "CHAT");
        given(configValidator.requireExecutable(0L, 100L, "CHAT")).willReturn(system);
        given(defaultMapper.selectOne(any())).willReturn(null);
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(defaultMapper).insert(any(LLMCapabilityDefault.class));

        assertThatThrownBy(() -> service.setSystemDefault("CHAT", 100L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(ErrorCode.LLM_DEFAULT_UPDATE_FAILED.getCode()));
    }

    private LLMModelConfig config(Long id, LLMConfigScope scope, Long ownerId, String capability) {
        LLMModelConfig config = new LLMModelConfig();
        config.setId(id);
        config.setScope(scope.name());
        config.setOwnerUserId(ownerId);
        config.setCapability(capability);
        config.setIsActive(true);
        return config;
    }

    private LLMCapabilityDefault defaultRow(
        LLMConfigScope scope, Long ownerId, String capability, Long configId) {
        LLMCapabilityDefault row = new LLMCapabilityDefault();
        row.setScope(scope.name());
        row.setOwnerUserId(ownerId);
        row.setCapability(capability);
        row.setConfigId(configId);
        return row;
    }
}
