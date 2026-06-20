package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.dto.request.UpdatePresetRequest;
import com.qingluo.link.model.enums.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SystemPresetServiceImpl} 单元测试，承接 acceptance 预设字段对齐与注册写入类。
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
    @DisplayName("注册镜像预设：直接平移预设自带 provider_type/protocol/api_base_url（不 join 厂商取值）")
    void applyPresetsForNewUser_movesPresetSelfCarriedFacts() {
        SystemPreset preset = preset(1L, 5L, "gte-rerank", "RERANK",
                "aliyun", "dashscope", "https://dashscope.aliyuncs.com/api/v1", "ENC_PLATFORM");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(preset));
        // 厂商存在性检查仍保留（跳过孤儿预设），但字段值取自 preset 而非 provider
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "aliyun", "https://other"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(0L);

        service.applyPresetsForNewUser(100L);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper).insert(captor.capture());
        UserLLMConfig written = captor.getValue();
        assertThat(written.getIsSystemPreset()).isTrue();
        assertThat(written.getIsDefault()).isTrue();
        assertThat(written.getApiKey()).isEqualTo("ENC_PLATFORM");
        assertThat(written.getProviderType()).isEqualTo("aliyun");
        assertThat(written.getProtocol()).isEqualTo("dashscope");
        assertThat(written.getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(written.getUserId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("注册写入幂等：已存在同预设行则跳过，不重复灌入")
    void applyPresetsForNewUser_idempotent() {
        SystemPreset preset = preset(1L, 5L, "deepseek-chat", "CHAT",
                "deepseek", "openai", "https://api.deepseek.com/v1", "ENC");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(preset));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(1L);

        service.applyPresetsForNewUser(100L);

        verify(userLLMConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("同一能力多条预设只让首条生效，避免单能力多生效")
    void applyPresetsForNewUser_singleDefaultPerCapability() {
        SystemPreset p1 = preset(1L, 5L, "deepseek-chat", "CHAT",
                "deepseek", "openai", "https://api.deepseek.com/v1", "ENC1");
        SystemPreset p2 = preset(2L, 5L, "deepseek-coder", "CHAT",
                "deepseek", "openai", "https://api.deepseek.com/v1", "ENC2");
        given(systemPresetMapper.selectList(any())).willReturn(List.of(p1, p2));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(userLLMConfigMapper.selectCount(any())).willReturn(0L);

        service.applyPresetsForNewUser(100L);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).filteredOn(c -> Boolean.TRUE.equals(c.getIsDefault())).hasSize(1);
    }

    @Test
    @DisplayName("创建预设：从模型能力层复制 protocol/api_base_url/provider_type，Key 加密入库")
    void createPreset_copiesModelFacts() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "aliyun", "https://other"));
        given(providerModelService.findActiveModelCapability(5L, "gte-rerank", "RERANK"))
                .willReturn(model("dashscope", "https://dashscope.aliyuncs.com/api/v1"));
        given(apiKeyEncryptService.encrypt("sk-platform")).willReturn("ENC_P");

        CreatePresetRequest request = new CreatePresetRequest();
        request.setProviderId(5L);
        request.setModelName("gte-rerank");
        request.setCapability("RERANK");
        request.setApiKey("sk-platform");

        SystemPreset result = service.createPreset(request);

        verify(systemPresetMapper).insert(any(SystemPreset.class));
        assertThat(result.getApiKey()).isEqualTo("ENC_P");
        assertThat(result.getCapability()).isEqualTo("RERANK");
        assertThat(result.getProtocol()).isEqualTo("dashscope");
        assertThat(result.getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(result.getProviderType()).isEqualTo("aliyun");
    }

    @Test
    @DisplayName("创建预设：目录中无该模型能力时拒绝（MODEL_NOT_SUPPORTED）")
    void createPreset_rejectsMissingModelCapability() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "deepseek", "url"));
        given(providerModelService.findActiveModelCapability(5L, "ghost", "CHAT")).willReturn(null);

        CreatePresetRequest request = new CreatePresetRequest();
        request.setProviderId(5L);
        request.setModelName("ghost");
        request.setCapability("CHAT");
        request.setApiKey("sk");

        assertThatThrownBy(() -> service.createPreset(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MODEL_NOT_SUPPORTED.getCode());
        verify(systemPresetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("更新预设绑定：重新复制模型能力层事实并加密新 Key")
    void updatePreset_rebindsModelFactsAndEncryptsKey() {
        SystemPreset existing = preset(1L, 5L, "deepseek-chat", "CHAT",
                "deepseek", "openai", "https://old", "ENC_OLD");
        given(systemPresetMapper.selectById(1L)).willReturn(existing);
        given(systemProviderMapper.selectById(6L)).willReturn(provider(6L, "aliyun", "https://other"));
        given(providerModelService.findActiveModelCapability(6L, "gte-rerank", "RERANK"))
                .willReturn(model("dashscope", "https://dashscope.aliyuncs.com/api/v1"));
        given(apiKeyEncryptService.encrypt("sk-new")).willReturn("ENC_NEW");

        UpdatePresetRequest request = new UpdatePresetRequest();
        request.setProviderId(6L);
        request.setModelName("gte-rerank");
        request.setCapability("rerank");
        request.setApiKey("sk-new");
        request.setIsActive(false);

        SystemPreset result = service.updatePreset(1L, request);

        assertThat(result.getProviderId()).isEqualTo(6L);
        assertThat(result.getProviderType()).isEqualTo("aliyun");
        assertThat(result.getCapability()).isEqualTo("RERANK");
        assertThat(result.getProtocol()).isEqualTo("dashscope");
        assertThat(result.getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(result.getApiKey()).isEqualTo("ENC_NEW");
        assertThat(result.getIsActive()).isFalse();
        verify(systemPresetMapper).updateById(existing);
    }

    @Test
    @DisplayName("启停预设：仅更新 is_active")
    void togglePreset_updatesActiveFlag() {
        SystemPreset existing = preset(1L, 5L, "deepseek-chat", "CHAT",
                "deepseek", "openai", "https://old", "ENC_OLD");
        given(systemPresetMapper.selectById(1L)).willReturn(existing);

        service.togglePreset(1L, false);

        assertThat(existing.getIsActive()).isFalse();
        verify(systemPresetMapper).updateById(existing);
    }

    private SystemPreset preset(Long id, Long providerId, String model, String capability,
                                String providerType, String protocol, String apiBaseUrl, String encKey) {
        SystemPreset preset = new SystemPreset();
        preset.setId(id);
        preset.setProviderId(providerId);
        preset.setModelName(model);
        preset.setCapability(capability);
        preset.setProviderType(providerType);
        preset.setProtocol(protocol);
        preset.setApiBaseUrl(apiBaseUrl);
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

    private ProviderModel model(String protocol, String apiBaseUrl) {
        ProviderModel m = new ProviderModel();
        m.setProtocol(protocol);
        m.setApiBaseUrl(apiBaseUrl);
        m.setIsActive(true);
        return m;
    }
}
