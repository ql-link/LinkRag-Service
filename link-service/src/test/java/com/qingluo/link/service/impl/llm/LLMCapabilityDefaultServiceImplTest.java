package com.qingluo.link.service.impl.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.model.dto.entity.LLMCapabilityDefault;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void setUserDefaultCreatesOneUserPointer() {
        LLMModelConfig config = config(101L, LLMConfigScope.USER, 7L, "CHAT");
        given(configValidator.requireExecutable(7L, 101L, "CHAT")).willReturn(config);
        given(defaultMapper.selectOne(any()))
            .willReturn(null, defaultRow(7L, "CHAT", 101L));

        CapabilityDefaultDTO result = service.setUserDefault(7L, "chat", 101L);

        assertThat(result.getConfigId()).isEqualTo(101L);
        verify(defaultMapper).insert(any(LLMCapabilityDefault.class));
    }

    @Test
    void setUserDefaultAcceptsAVisibleSystemConfig() {
        LLMModelConfig system = config(100L, LLMConfigScope.SYSTEM, 0L, "CHAT");
        given(configValidator.requireExecutable(7L, 100L, "CHAT")).willReturn(system);
        given(defaultMapper.selectOne(any()))
            .willReturn(null, defaultRow(7L, "CHAT", 100L));

        CapabilityDefaultDTO result = service.setUserDefault(7L, "chat", 100L);

        assertThat(result.getConfigId()).isEqualTo(100L);
        verify(defaultMapper).insert(any(LLMCapabilityDefault.class));
    }

    @Test
    void clearUserDefaultLeavesCapabilityUnconfiguredWithoutSystemFallback() {
        given(defaultMapper.selectOne(any())).willReturn(null);

        CapabilityDefaultDTO result = service.clearUserDefault(7L, "CHAT");

        assertThat(result.getConfigId()).isNull();
        verify(defaultMapper).delete(any());
    }

    @Test
    void missingUserDefaultIsReturnedAsUnconfigured() {
        given(defaultMapper.selectOne(any())).willReturn(null);

        CapabilityDefaultDTO result = service.getDefault(7L, "CHAT");

        assertThat(result.getCapability()).isEqualTo("CHAT");
        assertThat(result.getConfigId()).isNull();
    }

    @Test
    void clearUserDefaultsForSystemConfigDeletesEveryUserPointerToIt() {
        service.clearUserDefaultsForConfig(100L);

        verify(defaultMapper).delete(any());
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

    private LLMCapabilityDefault defaultRow(Long ownerId, String capability, Long configId) {
        LLMCapabilityDefault row = new LLMCapabilityDefault();
        row.setScope(LLMConfigScope.USER.name());
        row.setOwnerUserId(ownerId);
        row.setCapability(capability);
        row.setConfigId(configId);
        return row;
    }
}
