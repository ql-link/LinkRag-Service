package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.dto.request.UpdatePresetRequest;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.impl.llm.SystemPresetServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SystemPresetServiceImpl} 单元测试，承接系统兜底预设字段对齐与单能力默认唯一。
 */
@ExtendWith(MockitoExtension.class)
class SystemPresetServiceImplTest {

    @Mock
    private SystemPresetMapper systemPresetMapper;
    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private ProviderModelService providerModelService;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ApiKeyEncryptService apiKeyEncryptService;

    @InjectMocks
    private SystemPresetServiceImpl service;

    @BeforeAll
    static void initTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
                new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SystemPreset.class);
    }

    @Test
    @DisplayName("创建预设：从模型能力层复制 protocol/api_base_url/provider_type，Key 加密入库")
    void createPreset_copiesModelFacts() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "aliyun", "https://other"));
        given(providerModelService.findActiveModelCapability(5L, "gte-rerank", "RERANK"))
                .willReturn(model("GTE Rerank", "dashscope", "https://dashscope.aliyuncs.com/api/v1"));
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
        assertThat(result.getDisplayName()).isEqualTo("GTE Rerank");
        assertThat(result.getProtocol()).isEqualTo("dashscope");
        assertThat(result.getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(result.getProviderType()).isEqualTo("aliyun");
        assertThat(result.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("创建默认预设：清理同能力其他系统默认")
    void createPreset_asDefaultClearsOtherDefaults() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "linkrag", "https://linkrag"));
        given(providerModelService.findActiveModelCapability(5L, "linkrag-chat", "CHAT"))
                .willReturn(model("openai", "https://linkrag/v1/chat/completions"));
        given(apiKeyEncryptService.encrypt("sk-platform")).willReturn("ENC_P");

        CreatePresetRequest request = new CreatePresetRequest();
        request.setProviderId(5L);
        request.setModelName("linkrag-chat");
        request.setCapability("CHAT");
        request.setApiKey("sk-platform");
        request.setIsDefault(true);

        SystemPreset result = service.createPreset(request);

        assertThat(result.getProviderType()).isEqualTo("linkrag");
        assertThat(result.getIsDefault()).isTrue();
        verify(systemPresetMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(systemPresetMapper).insert(any(SystemPreset.class));
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
    @DisplayName("更新预设为默认：清理同能力其他系统默认")
    void updatePreset_setsDefaultAndClearsOthers() {
        SystemPreset existing = preset(1L, 5L, "linkrag-chat", "CHAT",
                "linkrag", "openai", "https://old", "ENC_OLD");
        given(systemPresetMapper.selectById(1L)).willReturn(existing);

        UpdatePresetRequest request = new UpdatePresetRequest();
        request.setIsDefault(true);

        SystemPreset result = service.updatePreset(1L, request);

        assertThat(result.getIsDefault()).isTrue();
        verify(systemPresetMapper).update(eq(null), any(LambdaUpdateWrapper.class));
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

    @Test
    @DisplayName("禁用当前系统默认预设时拒绝，避免兜底缺口")
    void togglePreset_rejectsDisablingDefaultPreset() {
        SystemPreset existing = preset(1L, 5L, "linkrag-chat", "CHAT",
                "linkrag", "openai", "https://old", "ENC_OLD");
        existing.setIsDefault(true);
        given(systemPresetMapper.selectById(1L)).willReturn(existing);

        assertThatThrownBy(() -> service.togglePreset(1L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前系统默认预设不能直接禁用，请先指定替代默认预设");
        verify(systemPresetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("显式设置系统默认预设：清理同能力其他默认并更新目标")
    void setDefaultPreset_setsDefault() {
        SystemPreset existing = preset(1L, 5L, "linkrag-chat", "CHAT",
                "linkrag", "openai", "https://old", "ENC_OLD");
        given(systemPresetMapper.selectById(1L)).willReturn(existing);

        service.setDefaultPreset(1L);

        assertThat(existing.getIsDefault()).isTrue();
        verify(systemPresetMapper).update(eq(null), any(LambdaUpdateWrapper.class));
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
        preset.setIsDefault(false);
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
        return model(null, protocol, apiBaseUrl);
    }

    private ProviderModel model(String displayName, String protocol, String apiBaseUrl) {
        ProviderModel m = new ProviderModel();
        m.setModelName("model");
        m.setDisplayName(displayName);
        m.setProtocol(protocol);
        m.setApiBaseUrl(apiBaseUrl);
        m.setIsActive(true);
        return m;
    }
}
