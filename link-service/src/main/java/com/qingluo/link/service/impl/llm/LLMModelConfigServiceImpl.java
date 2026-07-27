package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.mapper.LLMModelConfigMapper;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.LLMCapabilityDefault;
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
import com.qingluo.link.observability.log.AuditLog;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigService;
import com.qingluo.link.service.LLMModelConfigValidator;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.cache.LLMRuntimeCacheReadiness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * USER 与 SYSTEM 共用的一套可执行配置生命周期。
 */
@Service
@RequiredArgsConstructor
public class LLMModelConfigServiceImpl implements LLMModelConfigService {

    private static final long SYSTEM_OWNER_ID = 0L;
    private static final String LINKRAG_PROVIDER_TYPE = "linkrag";

    private final LLMModelConfigMapper configMapper;
    private final LLMCapabilityDefaultMapper defaultMapper;
    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final ProviderModelMapper providerModelMapper;
    private final SystemProviderMapper systemProviderMapper;
    private final SystemProviderService systemProviderService;
    private final ProviderModelService providerModelService;
    private final LLMCapabilityService capabilityService;
    private final LLMModelConfigValidator configValidator;
    private final LLMCapabilityDefaultService defaultService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;
    private final LLMRuntimeCacheReadiness runtimeCacheReadiness;

    @Override
    public List<ExecutableLLMConfigDTO> listVisibleConfigs(
        Long userId, String providerType, String capability, Boolean isActive) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        List<LLMModelConfig> configs = new ArrayList<>(configMapper.selectList(
            new LambdaQueryWrapper<LLMModelConfig>()
                .eq(LLMModelConfig::getScope, LLMConfigScope.USER.name())
                .eq(LLMModelConfig::getOwnerUserId, userId)));

        configs.addAll(configMapper.selectList(new LambdaQueryWrapper<LLMModelConfig>()
            .eq(LLMModelConfig::getScope, LLMConfigScope.SYSTEM.name())
            .eq(LLMModelConfig::getOwnerUserId, SYSTEM_OWNER_ID)));

        return toDTOs(configs.stream()
            .filter(config -> providerType == null || providerType.equals(config.getProviderType()))
            .filter(config -> normalizedCapability == null || normalizedCapability.equals(config.getCapability()))
            .filter(config -> isActive == null || isActive.equals(config.getIsActive()))
            .distinct()
            .sorted(configComparator())
            .toList(), userId);
    }

    @Override
    public List<ExecutableLLMConfigDTO> listSystemConfigs(String capability, Boolean isActive) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        List<LLMModelConfig> configs = configMapper.selectList(new LambdaQueryWrapper<LLMModelConfig>()
            .eq(LLMModelConfig::getScope, LLMConfigScope.SYSTEM.name())
            .eq(LLMModelConfig::getOwnerUserId, SYSTEM_OWNER_ID));
        List<ExecutableLLMConfigDTO> result = toDTOs(configs.stream()
            .filter(config -> normalizedCapability == null || normalizedCapability.equals(config.getCapability()))
            .filter(config -> isActive == null || isActive.equals(config.getIsActive()))
            .sorted(configComparator())
            .toList(), SYSTEM_OWNER_ID);
        result.forEach(config -> config.setEditable(true));
        return result;
    }

    @Override
    @Transactional
    public List<ExecutableLLMConfigDTO> setupProvider(Long userId, SetupProviderRequest request) {
        if (LINKRAG_PROVIDER_TYPE.equalsIgnoreCase(request.getProviderType())) {
            throw new BusinessException(ErrorCode.SYSTEM_PROVIDER_READONLY);
        }
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());
        List<ProviderModel> catalog = providerModelService.listActiveModels(provider.getId(), null);
        if (catalog.isEmpty()) {
            throw new BusinessException(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL);
        }
        catalog.forEach(this::requireModelFacts);

        String encryptedApiKey = apiKeyEncryptService.encrypt(request.getApiKey());
        List<LLMModelConfig> affected = new ArrayList<>();
        for (ProviderModel model : catalog) {
            LLMModelConfig config = findNaturalKey(
                LLMConfigScope.USER.name(), userId, provider.getId(), model.getModelName(), model.getCapability());
            if (config == null) {
                config = new LLMModelConfig();
                config.setScope(LLMConfigScope.USER.name());
                config.setOwnerUserId(userId);
                config.setProviderId(provider.getId());
                config.setProviderType(provider.getProviderType());
                config.setModelName(model.getModelName());
                config.setDisplayName(model.getDisplayName());
                config.setCapability(model.getCapability());
                config.setProtocol(model.getProtocol());
                config.setApiBaseUrl(model.getApiBaseUrl());
                config.setApiKey(encryptedApiKey);
                config.setIsActive(true);
                config.setSnapshotVersion(1L);
                configMapper.insert(config);
            } else {
                config.setProviderType(provider.getProviderType());
                config.setDisplayName(model.getDisplayName());
                config.setProtocol(model.getProtocol());
                config.setApiBaseUrl(model.getApiBaseUrl());
                config.setApiKey(encryptedApiKey);
                config.setSnapshotVersion(nextVersion(config));
                configMapper.updateById(config);
            }
            evictAfterCommit(config.getId());
            affected.add(config);
        }
        AuditLog.event("LLM_PROVIDER_SETUP", "userId={}, providerType={}, providerId={}, configRows={}",
            userId, provider.getProviderType(), provider.getId(), affected.size());
        return toDTOs(affected, userId);
    }

    @Override
    @Transactional
    public AdminPlatformConfigSaveResult saveSystemConfig(
        Long configId, AdminPlatformConfigSaveRequest request) {
        if (Boolean.TRUE.equals(request.getSetAsDefault())
            && Boolean.TRUE.equals(request.getClearDefault())) {
            throw new BusinessException(ErrorCode.LLM_DEFAULT_MUTATION_CONFLICT);
        }
        LLMModelConfig existing = configId == null ? null : requireScopeConfig(configId, true, null);
        ProviderFacts facts = resolveProviderFacts(existing, request);
        if (existing != null && !Objects.equals(existing.getCapability(), facts.capability())) {
            // 默认关系与数据集字段都带能力语义；允许原地改 capability 会让既有引用瞬间变成脏数据。
            throw new BusinessException(ErrorCode.LLM_CONFIG_CAPABILITY_MISMATCH,
                "已有配置不能原地修改能力，请创建新的平台配置");
        }

        LLMModelConfig config = existing;
        if (config == null) {
            config = findNaturalKey(LLMConfigScope.SYSTEM.name(), SYSTEM_OWNER_ID,
                facts.providerId(), facts.modelName(), facts.capability());
        }
        if (config == null) {
            if (!StringUtils.hasText(request.getApiKey())) {
                throw new BusinessException(ErrorCode.INVALID_API_KEY, "创建平台配置时API Key不能为空");
            }
            config = new LLMModelConfig();
            config.setScope(LLMConfigScope.SYSTEM.name());
            config.setOwnerUserId(SYSTEM_OWNER_ID);
            applyFacts(config, facts);
            config.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
            config.setIsActive(true);
            config.setSnapshotVersion(1L);
            configMapper.insert(config);
        } else {
            applyFacts(config, facts);
            if (StringUtils.hasText(request.getApiKey())) {
                config.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
            }
            config.setSnapshotVersion(nextVersion(config));
            configMapper.updateById(config);
        }
        evictAfterCommit(config.getId());

        String savedCapability = config.getCapability();
        CapabilityDefaultDTO defaultDTO;
        if (Boolean.TRUE.equals(request.getSetAsDefault())) {
            defaultDTO = defaultService.setSystemDefault(savedCapability, config.getId());
        } else if (Boolean.TRUE.equals(request.getClearDefault())) {
            defaultDTO = defaultService.clearSystemDefaultForConfig(savedCapability, config.getId());
        } else {
            defaultDTO = currentSystemDefault(savedCapability);
        }
        ExecutableLLMConfigDTO configDTO = toDTO(config, SYSTEM_OWNER_ID);
        configDTO.setEditable(true);
        return new AdminPlatformConfigSaveResult(configDTO, defaultDTO);
    }

    @Override
    @Transactional
    public void changeActive(Long actorUserId, boolean admin, Long configId, boolean isActive,
                             LLMConfigMutationMode mode, Long replacementConfigId, boolean confirmed) {
        LLMModelConfig config = requireScopeConfig(configId, admin, actorUserId);
        if (Objects.equals(config.getIsActive(), isActive)) {
            return;
        }
        if (isActive) {
            config.setIsActive(true);
            config.setSnapshotVersion(nextVersion(config));
            configMapper.updateById(config);
            evictAfterCommit(configId);
            return;
        }

        if (mode == LLMConfigMutationMode.STANDARD) {
            ensureNoDatasetReferences(configId);
            if (defaultService.isSystemDefault(configId)) {
                throw new BusinessException(ErrorCode.LLM_DEFAULT_REPLACEMENT_REQUIRED);
            }
        } else {
            prepareEmergencyDisable(config, admin, actorUserId, replacementConfigId, confirmed);
        }

        if (LLMConfigScope.USER.name().equals(config.getScope())) {
            defaultService.clearUserDefaultForConfig(actorUserId, configId);
        } else {
            defaultService.clearUserDefaultsForConfig(configId);
        }
        config.setIsActive(false);
        config.setSnapshotVersion(nextVersion(config));
        configMapper.updateById(config);
        evictAfterCommit(configId);
    }

    @Override
    @Transactional
    public void deleteConfig(Long actorUserId, boolean admin, Long configId) {
        LLMModelConfig config = requireScopeConfig(configId, admin, actorUserId);
        ensureNoDatasetReferences(configId);
        if (defaultService.isSystemDefault(configId)) {
            throw new BusinessException(ErrorCode.LLM_DEFAULT_REPLACEMENT_REQUIRED);
        }
        if (LLMConfigScope.USER.name().equals(config.getScope())) {
            defaultService.clearUserDefaultForConfig(actorUserId, configId);
        } else {
            defaultService.clearUserDefaultsForConfig(configId);
        }
        configMapper.deleteById(configId);
        evictAfterCommit(configId);
        AuditLog.event("LLM_CONFIG_DELETE", "actorUserId={}, scope={}, configId={}, capability={}",
            actorUserId, config.getScope(), configId, config.getCapability());
    }

    private void prepareEmergencyDisable(LLMModelConfig config, boolean admin, Long actorUserId,
                                         Long replacementConfigId, boolean confirmed) {
        if (LLMConfigScope.USER.name().equals(config.getScope())) {
            if (admin || !confirmed || !Objects.equals(config.getOwnerUserId(), actorUserId)) {
                throw new BusinessException(400, "紧急停用用户配置需要所有者明确确认", 400);
            }
            return;
        }
        if (!admin) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        if (!defaultService.isSystemDefault(config.getId())) {
            return;
        }
        if (replacementConfigId == null || replacementConfigId.equals(config.getId())) {
            throw new BusinessException(ErrorCode.LLM_DEFAULT_REPLACEMENT_REQUIRED);
        }
        LLMModelConfig replacement = configValidator.requireExecutable(
            SYSTEM_OWNER_ID, replacementConfigId, config.getCapability());
        if (!LLMConfigScope.SYSTEM.name().equals(replacement.getScope())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        defaultService.setSystemDefault(config.getCapability(), replacementConfigId);
    }

    private LLMModelConfig requireScopeConfig(Long configId, boolean admin, Long actorUserId) {
        LLMModelConfig config = configId == null ? null : configMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_NOT_FOUND);
        }
        if (admin) {
            if (!LLMConfigScope.SYSTEM.name().equals(config.getScope())) {
                throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
            }
        } else if (!LLMConfigScope.USER.name().equals(config.getScope())
            || !Objects.equals(actorUserId, config.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        return config;
    }

    private ProviderFacts resolveProviderFacts(
        LLMModelConfig existing, AdminPlatformConfigSaveRequest request) {
        if (request.getSourceProviderModelId() != null && request.getCatalogMutation() != null) {
            throw new BusinessException(400, "sourceProviderModelId 与 catalogMutation 只能提供一个", 400);
        }
        ProviderModel model = null;
        if (request.getSourceProviderModelId() != null) {
            model = providerModelMapper.selectById(request.getSourceProviderModelId());
            if (model == null || !Boolean.TRUE.equals(model.getIsActive())) {
                throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED);
            }
        } else if (request.getCatalogMutation() != null) {
            AdminPlatformConfigSaveRequest.CatalogMutation mutation = request.getCatalogMutation();
            if (mutation.getProviderId() == null) {
                throw new BusinessException(400, "catalogMutation.providerId不能为空", 400);
            }
            model = providerModelService.addModelCapability(
                mutation.getProviderId(), mutation.getModelName(), mutation.getDisplayName(),
                mutation.getCapability(), mutation.getProtocol(), mutation.getApiBaseUrl());
        }
        if (model == null) {
            if (existing == null) {
                throw new BusinessException(400, "创建平台配置必须提供模型目录事实", 400);
            }
            return new ProviderFacts(existing.getProviderId(), existing.getProviderType(),
                existing.getModelName(), existing.getDisplayName(), existing.getCapability(),
                existing.getProtocol(), existing.getApiBaseUrl());
        }
        requireModelFacts(model);
        SystemProvider provider = systemProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return new ProviderFacts(provider.getId(), provider.getProviderType(), model.getModelName(),
            model.getDisplayName(), normalizeCapability(model.getCapability()),
            model.getProtocol(), model.getApiBaseUrl());
    }

    private void applyFacts(LLMModelConfig config, ProviderFacts facts) {
        config.setProviderId(facts.providerId());
        config.setProviderType(facts.providerType());
        config.setModelName(facts.modelName());
        config.setDisplayName(facts.displayName());
        config.setCapability(facts.capability());
        config.setProtocol(facts.protocol());
        config.setApiBaseUrl(facts.apiBaseUrl());
    }

    private LLMModelConfig findNaturalKey(String scope, Long ownerUserId, Long providerId,
                                          String modelName, String capability) {
        return configMapper.selectOne(new LambdaQueryWrapper<LLMModelConfig>()
            .eq(LLMModelConfig::getScope, scope)
            .eq(LLMModelConfig::getOwnerUserId, ownerUserId)
            .eq(LLMModelConfig::getProviderId, providerId)
            .eq(LLMModelConfig::getModelName, modelName)
            .eq(LLMModelConfig::getCapability, normalizeCapability(capability))
            .last("LIMIT 1"));
    }

    /**
     * KEEP_CURRENT 只读取本次保存能力的当前平台默认快照。不能调用全能力默认解析，
     * 否则其它能力的一条脏默认关系会让本次互不相关的配置保存失败。
     */
    private CapabilityDefaultDTO currentSystemDefault(String capability) {
        LLMCapabilityDefault current = defaultMapper.selectOne(
            new LambdaQueryWrapper<LLMCapabilityDefault>()
                .eq(LLMCapabilityDefault::getScope, LLMConfigScope.SYSTEM.name())
                .eq(LLMCapabilityDefault::getOwnerUserId, SYSTEM_OWNER_ID)
                .eq(LLMCapabilityDefault::getCapability, capability)
                .last("LIMIT 1"));
        Long configId = current == null ? null : current.getConfigId();
        if (configId != null) {
            LLMModelConfig config = configValidator.requireExecutable(
                SYSTEM_OWNER_ID, configId, capability);
            if (!LLMConfigScope.SYSTEM.name().equals(config.getScope())
                || !Long.valueOf(SYSTEM_OWNER_ID).equals(config.getOwnerUserId())) {
                throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
            }
        }
        return new CapabilityDefaultDTO(capability, null, configId, configId);
    }

    private void ensureNoDatasetReferences(Long configId) {
        if (datasetParseConfigMapper.countModelReferences(configId) > 0) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_IN_USE);
        }
    }

    private void requireModelFacts(ProviderModel model) {
        if (!StringUtils.hasText(model.getModelName())
            || !StringUtils.hasText(model.getCapability())
            || !StringUtils.hasText(model.getProtocol())
            || !StringUtils.hasText(model.getApiBaseUrl())) {
            throw new BusinessException(ErrorCode.MODEL_CONFIG_INCOMPLETE);
        }
        normalizeCapability(model.getCapability());
    }

    private long nextVersion(LLMModelConfig config) {
        return config.getSnapshotVersion() == null ? 1L : config.getSnapshotVersion() + 1L;
    }

    private void evictAfterCommit(Long configId) {
        if (runtimeCacheReadiness.isEnabled()) {
            cacheConsistencyService.evict(CacheEvictTarget.LLM_RUNTIME_CONFIG, configId);
        }
    }

    private List<ExecutableLLMConfigDTO> toDTOs(Collection<LLMModelConfig> configs, Long actorUserId) {
        Set<Long> providerIds = configs.stream().map(LLMModelConfig::getProviderId).collect(Collectors.toSet());
        Map<Long, SystemProvider> providers = providerIds.isEmpty() ? Map.of()
            : systemProviderMapper.selectBatchIds(providerIds).stream()
                .collect(Collectors.toMap(SystemProvider::getId, Function.identity()));
        return configs.stream().map(config -> toDTO(config, actorUserId, providers.get(config.getProviderId()))).toList();
    }

    private ExecutableLLMConfigDTO toDTO(LLMModelConfig config, Long actorUserId) {
        return toDTO(config, actorUserId, systemProviderMapper.selectById(config.getProviderId()));
    }

    private ExecutableLLMConfigDTO toDTO(
        LLMModelConfig config, Long actorUserId, SystemProvider provider) {
        ExecutableLLMConfigDTO dto = new ExecutableLLMConfigDTO();
        dto.setConfigId(config.getId());
        dto.setScope(config.getScope());
        dto.setProviderId(config.getProviderId());
        dto.setProviderType(config.getProviderType());
        dto.setProviderName(provider == null ? null : provider.getProviderName());
        dto.setIconUrl(provider == null ? null : provider.getIconUrl());
        dto.setModelName(config.getModelName());
        dto.setDisplayName(StringUtils.hasText(config.getDisplayName())
            ? config.getDisplayName() : config.getModelName());
        dto.setCapability(config.getCapability());
        dto.setProtocol(config.getProtocol());
        dto.setApiBaseUrl(config.getApiBaseUrl());
        dto.setApiKeyMasked(apiKeyEncryptService.maskEncryptedApiKey(config.getApiKey()));
        dto.setIsActive(config.getIsActive());
        dto.setEditable(LLMConfigScope.USER.name().equals(config.getScope())
            && Objects.equals(actorUserId, config.getOwnerUserId()));
        dto.setSnapshotVersion(config.getSnapshotVersion());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }

    private Comparator<LLMModelConfig> configComparator() {
        return Comparator.comparing(LLMModelConfig::getCapability)
            .thenComparing(LLMModelConfig::getScope)
            .thenComparing(LLMModelConfig::getModelName);
    }

    private String normalizeCapability(String capability) {
        capabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }

    private String normalizeCapabilityIfPresent(String capability) {
        return StringUtils.hasText(capability) ? normalizeCapability(capability) : null;
    }

    private record ProviderFacts(Long providerId, String providerType, String modelName,
                                 String displayName, String capability, String protocol,
                                 String apiBaseUrl) {
    }
}
