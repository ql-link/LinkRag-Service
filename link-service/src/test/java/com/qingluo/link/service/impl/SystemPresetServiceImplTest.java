package com.qingluo.link.service.impl;

import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.impl.llm.SystemPresetServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SystemPresetServiceImpl} 单元测试，承接 acceptance 五类（系统预设注册写入与幂等）。
 */
@ExtendWith(MockitoExtension.class)
class SystemPresetServiceImplTest {

    @Mock
    private SystemPresetMapper systemPresetMapper;
    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;
    @Mock
    private ProviderModelService providerModelService;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ApiKeyEncryptService apiKeyEncryptService;

    @InjectMocks
    private SystemPresetServiceImpl service;

    @Test
    @DisplayName("五·注册写入预设：is_system_preset 与 is_default 为真、Key 密文原样搬运")
    void applyPresetsForNewUser_writesPresetRows() {
        SystemPreset preset = preset(1L, 5L, "deepseek-chat", "CHAT", "ENC_PLATFORM");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(preset));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "https://api.deepseek.com/v1"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(0L);

        service.applyPresetsForNewUser(100L);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper).insert(captor.capture());
        UserLLMConfig written = captor.getValue();
        assertThat(written.getIsSystemPreset()).isTrue();
        assertThat(written.getIsDefault()).isTrue();
        assertThat(written.getIsActive()).isTrue();
        assertThat(written.getApiKey()).isEqualTo("ENC_PLATFORM");
        assertThat(written.getProviderType()).isEqualTo("deepseek");
        assertThat(written.getApiBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(written.getUserId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("五·注册写入幂等：已存在同预设行则跳过，不重复灌入")
    void applyPresetsForNewUser_idempotent() {
        SystemPreset preset = preset(1L, 5L, "deepseek-chat", "CHAT", "ENC");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(preset));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(1L);

        service.applyPresetsForNewUser(100L);

        verify(userLLMConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("同一能力多条预设只让首条生效，避免单能力多生效")
    void applyPresetsForNewUser_singleDefaultPerCapability() {
        SystemPreset p1 = preset(1L, 5L, "deepseek-chat", "CHAT", "ENC1");
        SystemPreset p2 = preset(2L, 5L, "deepseek-coder", "CHAT", "ENC2");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(p1, p2));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(0L);

        service.applyPresetsForNewUser(100L);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).filteredOn(c -> Boolean.TRUE.equals(c.getIsDefault())).hasSize(1);
    }

    @Test
    @DisplayName("管理端新增预设：平台 Key 加密入库、校验目录支持")
    void createPreset_encryptsAndValidates() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(providerModelService.isModelCapabilityActive(5L, "deepseek-chat", "CHAT")).willReturn(true);
        given(apiKeyEncryptService.encrypt("sk-platform")).willReturn("ENC_P");

        CreatePresetRequest request = new CreatePresetRequest();
        request.setProviderId(5L);
        request.setModelName("deepseek-chat");
        request.setCapability("CHAT");
        request.setApiKey("sk-platform");

        SystemPreset result = service.createPreset(request);

        verify(systemPresetMapper).insert(any(SystemPreset.class));
        assertThat(result.getApiKey()).isEqualTo("ENC_P");
        assertThat(result.getCapability()).isEqualTo("CHAT");
    }

    private SystemPreset preset(Long id, Long providerId, String model, String capability, String encKey) {
        SystemPreset preset = new SystemPreset();
        preset.setId(id);
        preset.setProviderId(providerId);
        preset.setModelName(model);
        preset.setCapability(capability);
        preset.setApiKey(encKey);
        preset.setIsActive(true);
        return preset;
    }

    private SystemProvider provider(Long id, String type, String url) {
        SystemProvider provider = new SystemProvider();
        provider.setId(id);
        provider.setProviderType(type);
        provider.setApiBaseUrl(url);
        return provider;
    }
}
