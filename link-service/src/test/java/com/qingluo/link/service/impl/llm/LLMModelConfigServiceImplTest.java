package com.qingluo.link.service.impl.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.mapper.LLMModelConfigMapper;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.response.AdminPlatformConfigSaveResult;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.dto.response.ExecutableLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMConfigMutationMode;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.cache.LLMRuntimeCacheReadiness;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LLMModelConfigServiceImplTest {

    @Mock private LLMModelConfigMapper configMapper;
    @Mock private LLMCapabilityDefaultMapper defaultMapper;
    @Mock private DatasetParseConfigMapper datasetParseConfigMapper;
    @Mock private ProviderModelMapper providerModelMapper;
    @Mock private SystemProviderMapper systemProviderMapper;
    @Mock private SystemProviderService systemProviderService;
    @Mock private ProviderModelService providerModelService;
    @Mock private LLMCapabilityService capabilityService;
    @Mock private LLMModelConfigValidator configValidator;
    @Mock private LLMCapabilityDefaultService defaultService;
    @Mock private ApiKeyEncryptService apiKeyEncryptService;
    @Mock private CacheConsistencyService cacheConsistencyService;
    @Mock private LLMRuntimeCacheReadiness runtimeCacheReadiness;

    @InjectMocks
    private LLMModelConfigServiceImpl service;

    @Test
    void setupProviderRefreshesCiphertextWithoutChangingConfigIdOrActiveState() {
        SystemProvider provider = provider(20L, "openai");
        ProviderModel catalog = model(30L, 20L, "gpt-4o", "CHAT");
        LLMModelConfig existing = config(101L, LLMConfigScope.USER, 7L, "CHAT", false);
        existing.setProviderId(20L);
        existing.setProviderType("openai");
        existing.setModelName("gpt-4o");
        existing.setSnapshotVersion(1L);
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(providerModelService.listActiveModels(20L, null)).willReturn(List.of(catalog));
        given(apiKeyEncryptService.encrypt("new-key")).willReturn("cipher-v2");
        given(configMapper.selectOne(any())).willReturn(existing);
        given(systemProviderMapper.selectBatchIds(any())).willReturn(List.of(provider));
        given(runtimeCacheReadiness.isEnabled()).willReturn(true);

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("openai");
        request.setApiKey("new-key");
        List<ExecutableLLMConfigDTO> result = service.setupProvider(7L, request);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getConfigId()).isEqualTo(101L);
            assertThat(dto.getIsActive()).isFalse();
            assertThat(dto.getSnapshotVersion()).isEqualTo(2L);
        });
        assertThat(existing.getApiKey()).isEqualTo("cipher-v2");
        verify(configMapper, never()).insert(any());
        verify(configMapper).updateById(existing);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_RUNTIME_CONFIG, 101L);
    }

    @Test
    void standardDisableRejectsDatasetReferenceBeforeAnyWriteOrEviction() {
        LLMModelConfig existing = config(101L, LLMConfigScope.USER, 7L, "CHAT", true);
        given(configMapper.selectById(101L)).willReturn(existing);
        given(datasetParseConfigMapper.countModelReferences(101L)).willReturn(1L);

        assertThatThrownBy(() -> service.changeActive(7L, false, 101L, false,
            LLMConfigMutationMode.STANDARD, null, false))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.LLM_CONFIG_IN_USE.getCode()));

        verify(configMapper, never()).updateById(any());
        verify(cacheConsistencyService, never()).evict(any(), anyLong());
    }

    @Test
    void confirmedUserEmergencyDisableKeepsDatasetBindingAndClearsDefault() {
        LLMModelConfig existing = config(101L, LLMConfigScope.USER, 7L, "CHAT", true);
        existing.setSnapshotVersion(4L);
        given(configMapper.selectById(101L)).willReturn(existing);
        given(runtimeCacheReadiness.isEnabled()).willReturn(true);

        service.changeActive(7L, false, 101L, false,
            LLMConfigMutationMode.EMERGENCY, null, true);

        assertThat(existing.getIsActive()).isFalse();
        assertThat(existing.getSnapshotVersion()).isEqualTo(5L);
        verify(datasetParseConfigMapper, never()).countModelReferences(anyLong());
        verify(defaultService).clearUserDefaultForConfig(7L, 101L);
        verify(configMapper).updateById(existing);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_RUNTIME_CONFIG, 101L);
    }

    @Test
    void runtimeCacheNotReadySkipsFirstEvictionTarget() {
        LLMModelConfig existing = config(101L, LLMConfigScope.USER, 7L, "CHAT", false);
        existing.setSnapshotVersion(1L);
        given(configMapper.selectById(101L)).willReturn(existing);
        given(runtimeCacheReadiness.isEnabled()).willReturn(false);

        service.changeActive(7L, false, 101L, true,
            LLMConfigMutationMode.STANDARD, null, false);

        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getSnapshotVersion()).isEqualTo(2L);
        verify(configMapper).updateById(existing);
        verify(cacheConsistencyService, never()).evict(any(), anyLong());
    }

    @Test
    void adminCanCreateConfigWithoutExistingDefaultAndKeepCurrentDefaultEmpty() {
        ProviderModel catalog = model(30L, 20L, "gpt-4o", "CHAT");
        SystemProvider provider = provider(20L, "openai");
        given(providerModelMapper.selectById(30L)).willReturn(catalog);
        given(systemProviderMapper.selectById(20L)).willReturn(provider);
        given(configMapper.selectOne(any())).willReturn(null);
        given(apiKeyEncryptService.encrypt("platform-key")).willReturn("cipher");
        given(configMapper.insert(any(LLMModelConfig.class))).willAnswer(invocation -> {
            LLMModelConfig saved = invocation.getArgument(0);
            saved.setId(200L);
            return 1;
        });

        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        request.setSourceProviderModelId(30L);
        request.setApiKey("platform-key");
        request.setSetAsDefault(false);
        AdminPlatformConfigSaveResult result = service.saveSystemConfig(null, request);

        assertThat(result.getConfig().getConfigId()).isEqualTo(200L);
        assertThat(result.getConfig().getEditable()).isTrue();
        assertThat(result.getCapabilityDefault().getEffectiveConfigId()).isNull();
        verify(defaultService, never()).setSystemDefault(any(), anyLong());
        verify(defaultService, never()).listDefaults(anyLong());
    }

    @Test
    void adminCannotChangeCapabilityOfExistingGlobalConfig() {
        LLMModelConfig existing = config(200L, LLMConfigScope.SYSTEM, 0L, "CHAT", true);
        existing.setProviderId(20L);
        existing.setProviderType("openai");
        existing.setModelName("gpt-4o");
        ProviderModel embedding = model(31L, 20L, "text-embedding-3", "EMBEDDING");
        given(configMapper.selectById(200L)).willReturn(existing);
        given(providerModelMapper.selectById(31L)).willReturn(embedding);
        given(systemProviderMapper.selectById(20L)).willReturn(provider(20L, "openai"));

        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        request.setSourceProviderModelId(31L);

        assertThatThrownBy(() -> service.saveSystemConfig(200L, request))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(ErrorCode.LLM_CONFIG_CAPABILITY_MISMATCH.getCode()));
        verify(configMapper, never()).updateById(any());
        verify(cacheConsistencyService, never()).evict(any(), anyLong());
    }

    private LLMModelConfig config(
        Long id, LLMConfigScope scope, Long ownerId, String capability, boolean active) {
        LLMModelConfig config = new LLMModelConfig();
        config.setId(id);
        config.setScope(scope.name());
        config.setOwnerUserId(ownerId);
        config.setCapability(capability);
        config.setIsActive(active);
        config.setProtocol("openai");
        config.setApiBaseUrl("https://example.com/v1");
        config.setApiKey("cipher");
        return config;
    }

    private ProviderModel model(
        Long id, Long providerId, String modelName, String capability) {
        ProviderModel model = new ProviderModel();
        model.setId(id);
        model.setProviderId(providerId);
        model.setModelName(modelName);
        model.setDisplayName(modelName);
        model.setCapability(capability);
        model.setProtocol("openai");
        model.setApiBaseUrl("https://example.com/v1");
        model.setIsActive(true);
        return model;
    }

    private SystemProvider provider(Long id, String providerType) {
        SystemProvider provider = new SystemProvider();
        provider.setId(id);
        provider.setProviderType(providerType);
        provider.setProviderName("OpenAI");
        return provider;
    }
}
