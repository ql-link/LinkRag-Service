package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.cache.RagCacheSyncNotifier;
import com.qingluo.link.service.impl.llm.UserLLMConfigServiceImpl;

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

@ExtendWith(MockitoExtension.class)
class UserLLMConfigServiceImplTest {

    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;

    @Mock
    private SystemProviderService systemProviderService;

    @Mock
    private LLMCapabilityService llmCapabilityService;

    @Mock
    private ApiKeyEncryptService apiKeyEncryptService;

    @Mock
    private CacheConsistencyService cacheConsistencyService;

    @Mock
    private RagCacheSyncNotifier ragCacheSyncNotifier;

    @InjectMocks
    private UserLLMConfigServiceImpl service;

    @Test
    @DisplayName("Should_EvictConfigAndDefaultCache_When_UpdateConfig")
    void Should_EvictConfigAndDefaultCache_When_UpdateConfig() {
        UserLLMConfig config = buildConfig(11L, 7L);
        given(userLLMConfigMapper.selectById(11L)).willReturn(config);

        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setIsActive(false);

        service.updateConfig(7L, 11L, request);

        verify(userLLMConfigMapper).updateById(config);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
        verify(ragCacheSyncNotifier).notifyRefresh(7L, 11L);
    }

    @Test
    @DisplayName("Should_EvictConfigAndDefaultCache_When_DeleteConfig")
    void Should_EvictConfigAndDefaultCache_When_DeleteConfig() {
        UserLLMConfig config = buildConfig(11L, 7L);
        given(userLLMConfigMapper.selectById(11L)).willReturn(config);

        service.deleteConfig(7L, 11L);

        verify(userLLMConfigMapper).deleteById(11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
        verify(ragCacheSyncNotifier).notifyInvalidate(7L, 11L);
    }

    @Test
    @DisplayName("Should_InitializeProviderFields_When_CreateConfig")
    void Should_InitializeProviderFields_When_CreateConfig() {
        SystemProvider provider = new SystemProvider();
        provider.setId(5L);
        provider.setProviderType("openai");
        provider.setProviderName("OpenAI");
        provider.setSupportedCapabilities("[\"CHAT\"]");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("secret")).willReturn("encrypted");

        com.qingluo.link.model.dto.request.CreateConfigRequest request =
                new com.qingluo.link.model.dto.request.CreateConfigRequest();
        request.setProviderType("openai");
        request.setConfigName("cfg");
        request.setApiKey("secret");
        request.setModelName("gpt-4");
        request.setCapability("CHAT");

        service.createConfig(7L, request);

        verify(llmCapabilityService).ensureProviderSupports(provider, "CHAT");
        verify(userLLMConfigMapper).insert(any(UserLLMConfig.class));
    }

    @Test
    @DisplayName("Should_FallbackToSystemPreset_When_UserDefaultMissing")
    void Should_FallbackToSystemPreset_When_UserDefaultMissing() {
        UserLLMConfig systemPreset = buildConfig(100L, 0L);
        systemPreset.setModelName("qwen-plus");
        systemPreset.setCustomApiBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(null)
                .willReturn(systemPreset);

        UserLLMConfigDTO dto = service.getDefaultConfig(7L, "CHAT");

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getSystemPreset()).isTrue();
        assertThat(dto.getEditable()).isFalse();
        assertThat(dto.getSelectable()).isTrue();
        assertThat(dto.getModelName()).isNull();
        assertThat(dto.getCustomApiBaseUrl()).isNull();
        assertThat(dto.getApiKeyMasked()).isNull();
    }

    @Test
    @DisplayName("Should_ClearUserDefaultOnly_When_SelectSystemPreset")
    void Should_ClearUserDefaultOnly_When_SelectSystemPreset() {
        UserLLMConfig systemPreset = buildConfig(100L, 0L);
        given(userLLMConfigMapper.selectById(100L)).willReturn(systemPreset);

        service.setDefaultConfig(7L, 100L, "CHAT");

        verify(userLLMConfigMapper).update(any(), any());
        verify(userLLMConfigMapper, never()).updateById(systemPreset);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 100L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
        verify(ragCacheSyncNotifier).notifyRefresh(7L, 100L);
    }

    @Test
    @DisplayName("Should_RejectMutation_When_SystemPreset")
    void Should_RejectMutation_When_SystemPreset() {
        UserLLMConfig systemPreset = buildConfig(100L, 0L);
        given(userLLMConfigMapper.selectById(100L)).willReturn(systemPreset);

        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setIsActive(false);

        assertThatThrownBy(() -> service.updateConfig(7L, 100L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.SYSTEM_PRESET_READONLY.getCode()));
        verify(userLLMConfigMapper, never()).updateById(any(UserLLMConfig.class));
    }

    @Test
    @DisplayName("Should_RejectDelete_When_SystemPreset")
    void Should_RejectDelete_When_SystemPreset() {
        UserLLMConfig systemPreset = buildConfig(100L, 0L);
        given(userLLMConfigMapper.selectById(100L)).willReturn(systemPreset);

        assertThatThrownBy(() -> service.deleteConfig(7L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.SYSTEM_PRESET_READONLY.getCode()));
        verify(userLLMConfigMapper, never()).deleteById(100L);
    }

    private UserLLMConfig buildConfig(Long configId, Long userId) {
        UserLLMConfig config = new UserLLMConfig();
        config.setId(configId);
        config.setUserId(userId);
        config.setProviderType("openai");
        config.setProviderName("OpenAI");
        config.setConfigName("cfg");
        config.setApiKey("encrypted");
        config.setModelName("gpt-4");
        config.setCapability("CHAT");
        config.setPriority(50);
        config.setIsDefault(false);
        config.setIsActive(true);
        config.setTimeoutMs(60000);
        config.setMaxRetries(3);
        config.setStreamEnabled(true);
        return config;
    }
}
