package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.impl.llm.UserLLMConfigServiceImpl;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @InjectMocks
    private UserLLMConfigServiceImpl service;

    @Test
    @DisplayName("Should_EvictConfigAndDefaultCache_When_UpdateConfig")
    void Should_EvictConfigAndDefaultCache_When_UpdateConfig() {
        UserLLMConfig config = buildConfig(11L, 7L);
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(config);

        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setIsActive(false);

        service.updateConfig(7L, 11L, request);

        verify(userLLMConfigMapper).updateById(config);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("Should_EvictConfigAndDefaultCache_When_DeleteConfig")
    void Should_EvictConfigAndDefaultCache_When_DeleteConfig() {
        UserLLMConfig config = buildConfig(11L, 7L);
        given(userLLMConfigMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(config);

        service.deleteConfig(7L, 11L);

        verify(userLLMConfigMapper).deleteById(11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("Should_InitializeProviderFields_When_CreateConfig")
    void Should_InitializeProviderFields_When_CreateConfig() {
        SystemProvider provider = new SystemProvider();
        provider.setId(5L);
        provider.setProviderType("openai");
        provider.setProviderName("OpenAI");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(llmCapabilityService.getModelCapabilities(provider, "gpt-4")).willReturn(List.of("CHAT"));
        given(apiKeyEncryptService.encrypt("secret")).willReturn("encrypted");

        com.qingluo.link.model.dto.request.CreateConfigRequest request =
                new com.qingluo.link.model.dto.request.CreateConfigRequest();
        request.setProviderType("openai");
        request.setConfigName("cfg");
        request.setApiKey("secret");
        request.setModelName("gpt-4");

        service.createConfig(7L, request);

        verify(userLLMConfigMapper).insert(any(UserLLMConfig.class));
    }

    private UserLLMConfig buildConfig(Long configId, Long userId) {
        UserLLMConfig config = new UserLLMConfig();
        config.setId(configId);
        config.setUserId(userId);
        config.setProviderType("openai");
        config.setProviderName("OpenAI");
        config.setConfigName("cfg");
        config.setApiKey("encrypted");
        config.setIsDefault(false);
        config.setIsActive(true);
        return config;
    }
}
