package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.SelectEffectiveModelRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.request.ToggleModelRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.cache.UserLLMConfigCacheService;
import com.qingluo.link.service.impl.llm.UserLLMConfigServiceImpl;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link UserLLMConfigServiceImpl} 单元测试，承接 acceptance 二/三/四/五/六类 Scenario。
 */
@ExtendWith(MockitoExtension.class)
class UserLLMConfigServiceImplTest {

    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;
    @Mock
    private SystemPresetMapper systemPresetMapper;
    @Mock
    private SystemProviderService systemProviderService;
    @Mock
    private ProviderModelService providerModelService;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ApiKeyEncryptService apiKeyEncryptService;
    @Mock
    private CacheConsistencyService cacheConsistencyService;
    @Mock
    private UserLLMConfigCacheService userLLMConfigCacheService;

    @InjectMocks
    private UserLLMConfigServiceImpl service;

    @BeforeAll
    static void initTableInfoCache() {
        // 纯单元测试未启动 MyBatis-Plus 扫描，LambdaUpdateWrapper.set 解析列名需要列缓存；
        // 手动初始化 UserLLMConfig 的 TableInfo 以填充列缓存，避免 "can not find lambda cache"。
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
                new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), UserLLMConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SystemPreset.class);
    }

    // ============ 二、配置厂商 ============

    @Test
    @DisplayName("二·配置厂商自动展开整厂商模型，Key 厂商级共用同一密文且脱敏返回")
    void setupProvider_expandsAllModelsWithSharedEncryptedKey() {
        SystemProvider provider = providerOf(5L, "openai", "https://api.openai.com/v1");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("sk-alice")).willReturn("ENC");
        given(providerModelService.listActiveModels(5L, null)).willReturn(List.of(
                pm("gpt-4o", "CHAT"), pm("gpt-4o", "VISION"),
                pm("gpt-4o-mini", "CHAT"), pm("text-embedding-3", "EMBEDDING"),
                pm("bge-m3", "SPARSE_EMBEDDING")));
        given(userLLMConfigMapper.selectOne(any())).willReturn(null);
        given(apiKeyEncryptService.maskApiKey("ENC")).willReturn("EN****1234");

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("openai");
        request.setApiKey("sk-alice");

        List<UserLLMConfigDTO> result = service.setupProvider(7L, request);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper, times(5)).insert(captor.capture());
        // 厂商级 Key：5 行共用同一密文，且持久化的是密文而非明文
        assertThat(captor.getAllValues()).allMatch(c -> "ENC".equals(c.getApiKey()));
        // 协议与入口复制自模型能力层事实快照
        assertThat(captor.getAllValues()).allMatch(c -> "https://api.openai.com/v1".equals(c.getApiBaseUrl()));
        assertThat(captor.getAllValues()).allMatch(c -> "openai".equals(c.getProtocol()));
        assertThat(captor.getAllValues()).allMatch(c -> Boolean.TRUE.equals(c.getIsActive()));
        // 返回给用户的 Key 为脱敏形式，且均为自配行
        assertThat(result).hasSize(5);
        assertThat(result).allMatch(dto -> "EN****1234".equals(dto.getApiKeyMasked()));
        assertThat(result).allMatch(dto -> Boolean.FALSE.equals(dto.getIsSystemPreset()));
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("二·重复配置同一厂商更新其 Key 而非新增连接")
    void setupProvider_updatesKey_whenAlreadyConfigured() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("sk-new")).willReturn("ENC_NEW");
        given(providerModelService.listActiveModels(5L, null)).willReturn(List.of(pm("gpt-4o", "CHAT")));
        UserLLMConfig existing = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        existing.setApiKey("ENC_OLD");
        given(userLLMConfigMapper.selectOne(any())).willReturn(existing);
        given(apiKeyEncryptService.maskApiKey("ENC_NEW")).willReturn("EN****");

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("openai");
        request.setApiKey("sk-new");

        service.setupProvider(7L, request);

        verify(userLLMConfigMapper, never()).insert(any());
        verify(userLLMConfigMapper).updateById(existing);
        assertThat(existing.getApiKey()).isEqualTo("ENC_NEW");
        // 重复展开同时刷新协议与入口快照
        assertThat(existing.getProtocol()).isEqualTo("openai");
        assertThat(existing.getApiBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    @DisplayName("二·同厂商不同能力展开为不同协议快照（protocol+capability 矩阵）")
    void setupProvider_sameProviderMultiProtocolSnapshot() {
        SystemProvider provider = providerOf(5L, "aliyun", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        given(systemProviderService.getActiveByProviderType("aliyun")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("sk")).willReturn("ENC");
        given(providerModelService.listActiveModels(5L, null)).willReturn(List.of(
                pm("qwen-max", "CHAT", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                pm("qwen-embedding", "EMBEDDING", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                pm("qwen-sparse", "SPARSE_EMBEDDING", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                pm("gte-rerank", "RERANK", "dashscope", "https://dashscope.aliyuncs.com/api/v1"),
                pm("qwen3-asr", "ASR", "dashscope", "https://dashscope.aliyuncs.com/api/v1")));
        given(userLLMConfigMapper.selectOne(any())).willReturn(null);

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("aliyun");
        request.setApiKey("sk");
        service.setupProvider(7L, request);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper, times(5)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserLLMConfig::getCapability, UserLLMConfig::getProtocol)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CHAT", "openai"),
                        org.assertj.core.groups.Tuple.tuple("EMBEDDING", "openai"),
                        org.assertj.core.groups.Tuple.tuple("SPARSE_EMBEDDING", "openai"),
                        org.assertj.core.groups.Tuple.tuple("RERANK", "dashscope"),
                        org.assertj.core.groups.Tuple.tuple("ASR", "dashscope"));
    }

    @Test
    @DisplayName("二·用户配置入口复制模型能力事实，不 fallback 到厂商默认入口")
    void setupProvider_doesNotFallbackToProviderDefaultUrl() {
        // 厂商默认入口与模型能力真实入口不同，必须取模型能力的
        SystemProvider provider = providerOf(5L, "aliyun", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        given(systemProviderService.getActiveByProviderType("aliyun")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("sk")).willReturn("ENC");
        given(providerModelService.listActiveModels(5L, null)).willReturn(List.of(
                pm("gte-rerank", "RERANK", "dashscope", "https://dashscope.aliyuncs.com/api/v1")));
        given(userLLMConfigMapper.selectOne(any())).willReturn(null);

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("aliyun");
        request.setApiKey("sk");
        service.setupProvider(7L, request);

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(userLLMConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(captor.getValue().getApiBaseUrl()).isNotEqualTo(provider.getApiBaseUrl());
        assertThat(captor.getValue().getProtocol()).isEqualTo("dashscope");
    }

    @Test
    @DisplayName("二·展开遇缺协议或入口的模型能力时阻断（MODEL_CONFIG_INCOMPLETE）")
    void setupProvider_blocksIncompleteModelFact() {
        SystemProvider provider = providerOf(5L, "openai", "https://api.openai.com/v1");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(apiKeyEncryptService.encrypt("sk")).willReturn("ENC");
        given(providerModelService.listActiveModels(5L, null)).willReturn(List.of(
                pm("bad-model", "CHAT", null, null)));

        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("openai");
        request.setApiKey("sk");

        assertThatThrownBy(() -> service.setupProvider(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MODEL_CONFIG_INCOMPLETE.getCode());
        verify(userLLMConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("二·LinkRag 是系统兜底厂商，不允许用户配置 Key")
    void setupProvider_rejectsLinkRag() {
        SetupProviderRequest request = new SetupProviderRequest();
        request.setProviderType("linkrag");
        request.setApiKey("sk-user");

        assertThatThrownBy(() -> service.setupProvider(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SYSTEM_PROVIDER_READONLY.getCode());
        verify(systemProviderService, never()).getActiveByProviderType(any());
        verify(userLLMConfigMapper, never()).insert(any());
    }

    // ============ 三、模型启停 ============

    @Test
    @DisplayName("三·模型启停按厂商+模型批量更新启用状态并失效生效缓存")
    void toggleModel_batchUpdatesActive() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);

        ToggleModelRequest request = new ToggleModelRequest();
        request.setProviderType("openai");
        request.setModelName("gpt-4o-mini");
        request.setEnabled(false);

        service.toggleModel(7L, request);

        verify(userLLMConfigMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("三·带 capability 时只启停当前模型能力行")
    void toggleModel_withCapability_updatesOnlyOneConfig() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o", "VISION");
        given(userLLMConfigMapper.selectOne(any())).willReturn(cfg);

        ToggleModelRequest request = new ToggleModelRequest();
        request.setProviderType("openai");
        request.setModelName("gpt-4o");
        request.setCapability("vision");
        request.setEnabled(false);

        service.toggleModel(7L, request);

        assertThat(cfg.getIsActive()).isFalse();
        verify(userLLMConfigMapper).updateById(cfg);
        verify(userLLMConfigMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("三·关闭当前能力用户默认配置时清除默认标记以回退 LinkRag")
    void toggleModel_withCapability_clearsDefaultWhenDisabled() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        cfg.setIsDefault(true);
        given(userLLMConfigMapper.selectOne(any())).willReturn(cfg);

        ToggleModelRequest request = new ToggleModelRequest();
        request.setProviderType("openai");
        request.setModelName("gpt-4o");
        request.setCapability("CHAT");
        request.setEnabled(false);

        service.toggleModel(7L, request);

        assertThat(cfg.getIsActive()).isFalse();
        assertThat(cfg.getIsDefault()).isFalse();
        verify(userLLMConfigMapper).updateById(cfg);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("三·能力级启停找不到用户自配配置时返回 USER_CONFIG_NOT_FOUND")
    void toggleModel_withCapability_throwsWhenSelfConfigMissing() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(userLLMConfigMapper.selectOne(any())).willReturn(null);

        ToggleModelRequest request = new ToggleModelRequest();
        request.setProviderType("openai");
        request.setModelName("gpt-4o");
        request.setCapability("CHAT");
        request.setEnabled(false);

        assertThatThrownBy(() -> service.toggleModel(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.USER_CONFIG_NOT_FOUND.getCode());
        verify(userLLMConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("三·LinkRag 系统兜底厂商不可通过模型启停接口修改")
    void toggleModel_rejectsLinkRag() {
        ToggleModelRequest request = new ToggleModelRequest();
        request.setProviderType("linkrag");
        request.setModelName("linkrag-chat");
        request.setCapability("CHAT");
        request.setEnabled(false);

        assertThatThrownBy(() -> service.toggleModel(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SYSTEM_PROVIDER_READONLY.getCode());
        verify(systemProviderService, never()).getActiveByProviderType(any());
        verify(userLLMConfigMapper, never()).updateById(any());
        verify(userLLMConfigMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    // ============ 四、按能力选生效 ============

    @Test
    @DisplayName("四·为某能力选定启用模型成为生效配置")
    void selectEffectiveModel_setsDefault() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(providerModelService.isModelCapabilityActive(5L, "gpt-4o", "CHAT")).willReturn(true);
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        given(userLLMConfigMapper.selectOne(any())).willReturn(cfg);

        SelectEffectiveModelRequest request = effectiveReq("CHAT", "openai", "gpt-4o");
        service.selectEffectiveModel(7L, request);

        assertThat(cfg.getIsDefault()).isTrue();
        verify(userLLMConfigMapper).update(eq(null), any(LambdaUpdateWrapper.class)); // clearOtherDefault
        verify(userLLMConfigMapper).updateById(cfg);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("四·选择 LinkRag 只读配置时清空用户默认并回退系统兜底")
    void selectEffectiveModel_linkRagClearsUserDefault() {
        SystemPreset preset = linkRagPreset(100L, "CHAT", "linkrag-chat");
        given(systemPresetMapper.selectOne(any())).willReturn(preset);

        SelectEffectiveModelRequest request = effectiveReq("chat", "linkrag", "linkrag-chat");
        service.selectEffectiveModel(7L, request);

        verify(userLLMConfigMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
        verify(systemProviderService, never()).getActiveByProviderType(any());
    }

    @Test
    @DisplayName("四·不能选择已关闭的模型作为生效（MODEL_DISABLED）")
    void selectEffectiveModel_rejectsDisabledModel() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(providerModelService.isModelCapabilityActive(5L, "gpt-4o-mini", "CHAT")).willReturn(true);
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o-mini", "CHAT");
        cfg.setIsActive(false);
        given(userLLMConfigMapper.selectOne(any())).willReturn(cfg);

        SelectEffectiveModelRequest request = effectiveReq("CHAT", "openai", "gpt-4o-mini");

        assertThatThrownBy(() -> service.selectEffectiveModel(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MODEL_DISABLED.getCode());
        verify(userLLMConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("四·不能为模型不支持的能力选定生效（MODEL_NOT_SUPPORTED）")
    void selectEffectiveModel_rejectsUnsupportedCapability() {
        SystemProvider provider = providerOf(5L, "openai", "url");
        given(systemProviderService.getActiveByProviderType("openai")).willReturn(provider);
        given(providerModelService.isModelCapabilityActive(5L, "gpt-4o", "EMBEDDING")).willReturn(false);

        SelectEffectiveModelRequest request = effectiveReq("EMBEDDING", "openai", "gpt-4o");

        assertThatThrownBy(() -> service.selectEffectiveModel(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MODEL_NOT_SUPPORTED.getCode());
        verify(userLLMConfigMapper, never()).updateById(any());
    }

    // ============ 五、删除自配 + 取自配默认 ============

    @Test
    @DisplayName("删除自配行成功并失效缓存")
    void deleteConfig_deletesSelfConfig() {
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        given(userLLMConfigMapper.selectOne(any())).willReturn(cfg);

        service.deleteConfig(7L, 11L);

        verify(userLLMConfigMapper).deleteById(11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.LLM_CONFIG, 11L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("清空某能力用户自配默认，后续有效配置解析回退系统兜底")
    void clearDefaultConfig_clearsUserDefault() {
        service.clearDefaultConfig(7L, "chat");

        verify(userLLMConfigMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(cacheConsistencyService).evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, 7L);
    }

    @Test
    @DisplayName("六·按能力取到生效配置")
    void getDefaultConfig_returnsEffective() {
        UserLLMConfig cfg = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        cfg.setIsDefault(true);
        given(apiKeyEncryptService.maskApiKey("ENC")).willReturn("EN****");
        UserLLMConfigDTO dto = toDto(cfg);
        given(userLLMConfigCacheService.getOrLoadAll(eq(7L), any())).willReturn(List.of(dto));

        UserLLMConfigDTO result = service.getDefaultConfig(7L, "CHAT");

        assertThat(result.getModelName()).isEqualTo("gpt-4o");
        assertThat(result.getApiKeyMasked()).isEqualTo("EN****");
    }

    @Test
    @DisplayName("六·无生效配置返回 NO_DEFAULT_CONFIG")
    void getDefaultConfig_throwsWhenNone() {
        given(userLLMConfigCacheService.getOrLoadAll(eq(7L), any())).willReturn(List.of());

        assertThatThrownBy(() -> service.getDefaultConfig(7L, "CHAT"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NO_DEFAULT_CONFIG.getCode());
    }

    @Test
    @DisplayName("读取配置列表命中用户配置缓存后在内存过滤")
    void getConfigs_filtersCachedUserConfigsInMemory() {
        UserLLMConfig chat = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        UserLLMConfig embedding = config(12L, 7L, "openai", "text-embedding-3", "EMBEDDING");
        embedding.setIsActive(false);
        given(apiKeyEncryptService.maskApiKey("ENC")).willReturn("EN****");
        List<UserLLMConfigDTO> cached = List.of(toDto(chat), toDto(embedding));
        given(userLLMConfigCacheService.getOrLoadAll(eq(7L), any()))
                .willReturn(cached);
        given(systemPresetMapper.selectList(any())).willReturn(List.of());

        List<UserLLMConfigDTO> result = service.getConfigs(7L, "openai", "CHAT", true);

        assertThat(result).extracting(UserLLMConfigDTO::getModelName).containsExactly("gpt-4o");
        verify(userLLMConfigMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("读取配置列表合并 LinkRag 只读配置，用户无默认时 LinkRag 为生效")
    void getConfigs_appendsReadonlyLinkRagConfig() {
        given(userLLMConfigCacheService.getOrLoadAll(eq(7L), any())).willReturn(List.of());
        given(systemPresetMapper.selectList(any())).willReturn(List.of(linkRagPreset(100L, "CHAT", "linkrag-chat")));
        given(apiKeyEncryptService.maskApiKey("ENC_SYS")).willReturn("SY****");

        List<UserLLMConfigDTO> result = service.getConfigs(7L, null, "chat", true);

        assertThat(result).hasSize(1);
        UserLLMConfigDTO linkRag = result.get(0);
        assertThat(linkRag.getProviderType()).isEqualTo("linkrag");
        assertThat(linkRag.getModelName()).isEqualTo("linkrag-chat");
        assertThat(linkRag.getIsEditable()).isFalse();
        assertThat(linkRag.getIsDefault()).isTrue();
        assertThat(linkRag.getApiKeyMasked()).isEqualTo("SY****");
    }

    @Test
    @DisplayName("读取配置列表存在用户默认时 LinkRag 可用但不是当前生效")
    void getConfigs_linkRagNotDefaultWhenUserDefaultExists() {
        UserLLMConfig userDefault = config(11L, 7L, "openai", "gpt-4o", "CHAT");
        userDefault.setIsDefault(true);
        given(apiKeyEncryptService.maskApiKey("ENC")).willReturn("EN****");
        UserLLMConfigDTO userDefaultDto = toDto(userDefault);
        given(userLLMConfigCacheService.getOrLoadAll(eq(7L), any())).willReturn(List.of(userDefaultDto));
        given(systemPresetMapper.selectList(any())).willReturn(List.of(linkRagPreset(100L, "CHAT", "linkrag-chat")));
        given(apiKeyEncryptService.maskApiKey("ENC_SYS")).willReturn("SY****");

        List<UserLLMConfigDTO> result = service.getConfigs(7L, null, "chat", true);

        assertThat(result).extracting(UserLLMConfigDTO::getProviderType)
                .containsExactlyInAnyOrder("openai", "linkrag");
        UserLLMConfigDTO linkRag = result.stream()
                .filter(dto -> "linkrag".equals(dto.getProviderType()))
                .findFirst()
                .orElseThrow();
        assertThat(linkRag.getIsEditable()).isFalse();
        assertThat(linkRag.getIsDefault()).isFalse();
    }

    // ============ helpers ============

    private SystemProvider providerOf(Long id, String type, String url) {
        SystemProvider provider = new SystemProvider();
        provider.setId(id);
        provider.setProviderType(type);
        provider.setProviderName(type);
        provider.setApiBaseUrl(url);
        provider.setIsActive(true);
        return provider;
    }

    private ProviderModel pm(String model, String capability) {
        return pm(model, capability, "openai", "https://api.openai.com/v1");
    }

    private ProviderModel pm(String model, String capability, String protocol, String apiBaseUrl) {
        ProviderModel m = new ProviderModel();
        m.setModelName(model);
        m.setCapability(capability);
        m.setProtocol(protocol);
        m.setApiBaseUrl(apiBaseUrl);
        m.setIsActive(true);
        return m;
    }

    private UserLLMConfig config(Long id, Long userId, String type, String model, String capability) {
        UserLLMConfig config = new UserLLMConfig();
        config.setId(id);
        config.setUserId(userId);
        config.setProviderId(5L);
        config.setProviderType(type);
        config.setModelName(model);
        config.setCapability(capability);
        config.setApiKey("ENC");
        config.setApiBaseUrl("url");
        config.setIsActive(true);
        config.setIsDefault(false);
        config.setIsSystemPreset(false);
        return config;
    }

    private UserLLMConfigDTO toDto(UserLLMConfig config) {
        UserLLMConfigDTO dto = new UserLLMConfigDTO();
        dto.setId(config.getId());
        dto.setProviderType(config.getProviderType());
        dto.setModelName(config.getModelName());
        dto.setCapability(config.getCapability());
        dto.setApiKeyMasked(apiKeyEncryptService.maskApiKey(config.getApiKey()));
        dto.setApiBaseUrl(config.getApiBaseUrl());
        dto.setProtocol(config.getProtocol());
        dto.setIsActive(config.getIsActive());
        dto.setIsDefault(config.getIsDefault());
        dto.setIsSystemPreset(config.getIsSystemPreset());
        dto.setIsEditable(true);
        return dto;
    }

    private SystemPreset linkRagPreset(Long id, String capability, String modelName) {
        SystemPreset preset = new SystemPreset();
        preset.setId(id);
        preset.setProviderId(99L);
        preset.setProviderType("linkrag");
        preset.setModelName(modelName);
        preset.setCapability(capability);
        preset.setApiKey("ENC_SYS");
        preset.setApiBaseUrl("https://api.linkrag.local/v1");
        preset.setProtocol("openai");
        preset.setIsActive(true);
        preset.setIsDefault(true);
        return preset;
    }

    private SelectEffectiveModelRequest effectiveReq(String capability, String type, String model) {
        SelectEffectiveModelRequest request = new SelectEffectiveModelRequest();
        request.setCapability(capability);
        request.setProviderType(type);
        request.setModelName(model);
        return request;
    }
}
