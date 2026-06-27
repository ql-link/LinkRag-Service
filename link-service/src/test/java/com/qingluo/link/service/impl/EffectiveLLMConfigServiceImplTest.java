package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.response.EffectiveLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.impl.llm.EffectiveLLMConfigServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link EffectiveLLMConfigServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class EffectiveLLMConfigServiceImplTest {

    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;
    @Mock
    private SystemPresetMapper systemPresetMapper;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ApiKeyEncryptService apiKeyEncryptService;

    @InjectMocks
    private EffectiveLLMConfigServiceImpl service;

    @Test
    @DisplayName("用户存在自配默认时优先返回 USER 配置引用")
    void getEffectiveConfig_prefersUserDefault() {
        UserLLMConfig userConfig = userConfig();
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(userConfig);
        given(apiKeyEncryptService.maskApiKey("ENC_USER")).willReturn("EN****USER");

        EffectiveLLMConfigDTO result = service.getEffectiveConfig(7L, "chat");

        assertThat(result.getSource()).isEqualTo("USER");
        assertThat(result.getConfigId()).isEqualTo(11L);
        assertThat(result.getProviderType()).isEqualTo("openai");
        assertThat(result.getApiKeyMasked()).isEqualTo("EN****USER");
        verify(systemPresetMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("用户无自配默认时回退 LinkRag 系统默认预设")
    void getEffectiveConfig_fallsBackToSystemPreset() {
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
        given(systemPresetMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(systemPreset());
        given(apiKeyEncryptService.maskApiKey("ENC_SYSTEM")).willReturn("EN****SYS");

        EffectiveLLMConfigDTO result = service.getEffectiveConfig(7L, "CHAT");

        assertThat(result.getSource()).isEqualTo("SYSTEM");
        assertThat(result.getConfigId()).isEqualTo(100L);
        assertThat(result.getProviderType()).isEqualTo("linkrag");
        assertThat(result.getModelName()).isEqualTo("linkrag-chat");
        assertThat(result.getApiKeyMasked()).isEqualTo("EN****SYS");
    }

    @Test
    @DisplayName("用户与系统都无默认配置时返回 NO_DEFAULT_CONFIG")
    void getEffectiveConfig_throwsWhenNoUserOrSystemDefault() {
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
        given(systemPresetMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

        assertThatThrownBy(() -> service.getEffectiveConfig(7L, "CHAT"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NO_DEFAULT_CONFIG.getCode());
    }

    private UserLLMConfig userConfig() {
        UserLLMConfig config = new UserLLMConfig();
        config.setId(11L);
        config.setProviderId(5L);
        config.setProviderType("openai");
        config.setModelName("gpt-4o");
        config.setCapability("CHAT");
        config.setProtocol("openai");
        config.setApiBaseUrl("https://api.openai.com/v1/chat/completions");
        config.setApiKey("ENC_USER");
        return config;
    }

    private SystemPreset systemPreset() {
        SystemPreset preset = new SystemPreset();
        preset.setId(100L);
        preset.setProviderId(99L);
        preset.setProviderType("linkrag");
        preset.setModelName("linkrag-chat");
        preset.setCapability("CHAT");
        preset.setProtocol("openai");
        preset.setApiBaseUrl("https://api.linkrag.local/v1/chat/completions");
        preset.setApiKey("ENC_SYSTEM");
        return preset;
    }
}
