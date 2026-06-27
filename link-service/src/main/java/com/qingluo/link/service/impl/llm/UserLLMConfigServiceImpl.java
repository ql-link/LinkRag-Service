package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.log.AuditLog;
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
import com.qingluo.link.service.UserLLMConfigService;
import com.qingluo.link.service.cache.UserLLMConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 用户 LLM 配置服务实现。
 *
 * <p>用户配置表只保存用户自配行（is_system_preset=false），按能力多行存储，靠 is_default
 * 表达「某能力用户自配当前生效哪一条」。系统兜底配置由 llm_system_preset 提供，不再注册镜像到本表。
 * Key 为厂商级——同厂商多行共用同一个加密 Key。缓存失效统一走 {@link CacheConsistencyService}。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private static final String LINKRAG_PROVIDER_TYPE = "linkrag";

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemPresetMapper systemPresetMapper;
    private final SystemProviderService systemProviderService;
    private final ProviderModelService providerModelService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;
    private final UserLLMConfigCacheService userLLMConfigCacheService;

    @Override
    /**
     * 按条件查询可用 LLM 配置列表，支持按能力过滤。
     *
     * <p>返回用户自配配置 + LinkRag 只读系统配置。前端不需要理解系统预设；
     * 只需要按 isEditable 控制是否允许编辑/删除/启停。</p>
     */
    public List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, String capability, Boolean isActive) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);

        List<UserLLMConfigDTO> userConfigs = userLLMConfigCacheService.getOrLoadAll(userId, () -> loadAllConfigDTOs(userId));
        List<UserLLMConfigDTO> configs = new ArrayList<>(userConfigs);
        configs.addAll(loadLinkRagConfigDTOs(normalizedCapability, userConfigs));

        return configs.stream()
                .filter(dto -> providerType == null || providerType.equals(dto.getProviderType()))
                .filter(dto -> normalizedCapability == null || normalizedCapability.equals(dto.getCapability()))
                .filter(dto -> isActive == null || isActive.equals(dto.getIsActive()))
                .toList();
    }

    /**
     * 从数据库加载当前用户全部配置并转 DTO。调用方在缓存命中后做内存过滤，避免不同筛选条件拆出多份缓存。
     */
    private List<UserLLMConfigDTO> loadAllConfigDTOs(Long userId) {
        LambdaQueryWrapper<UserLLMConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLLMConfig::getUserId, userId);
        wrapper.eq(UserLLMConfig::getIsSystemPreset, false);
        return userLLMConfigMapper.selectList(wrapper).stream().map(this::toDTO).toList();
    }

    /**
     * 加载 LinkRag 系统默认配置并按前端统一配置项返回。
     *
     * <p>LinkRag 是否为当前生效由用户自配默认是否存在决定：同能力存在启用的用户默认时，
     * LinkRag 仍可使用但不是当前生效；否则它就是该能力的生效配置。</p>
     */
    private List<UserLLMConfigDTO> loadLinkRagConfigDTOs(String normalizedCapability, List<UserLLMConfigDTO> userConfigs) {
        LambdaQueryWrapper<SystemPreset> wrapper = new LambdaQueryWrapper<SystemPreset>()
                .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
                .eq(SystemPreset::getIsDefault, true)
                .eq(SystemPreset::getIsActive, true);
        if (normalizedCapability != null) {
            wrapper.eq(SystemPreset::getCapability, normalizedCapability);
        }

        return systemPresetMapper.selectList(wrapper).stream()
                .map(preset -> toLinkRagDTO(preset, hasActiveUserDefault(userConfigs, preset.getCapability())))
                .toList();
    }

    @Override
    @Transactional
    /**
     * 配置厂商（第一步）：选厂商 + 填厂商级 Key，按模型能力目录展开该厂商全部
     * (模型, 能力) 写入用户配置。
     *
     * <p>只创建/更新自配行（is_system_preset=false），永不触碰预设行；故重复配置同一厂商
     * 时按 (用户,厂商,模型,能力,非预设) 命中既有自配行并更新其 Key/地址，不新增第二个连接。
     * protocol/api_base_url 复制自模型能力层事实快照，缺失即抛 MODEL_CONFIG_INCOMPLETE，绝不 fallback 厂商默认。</p>
     */
    public List<UserLLMConfigDTO> setupProvider(Long userId, SetupProviderRequest request) {
        if (LINKRAG_PROVIDER_TYPE.equals(request.getProviderType())) {
            throw new BusinessException(ErrorCode.SYSTEM_PROVIDER_READONLY);
        }
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());
        String encryptedApiKey = apiKeyEncryptService.encrypt(request.getApiKey());

        // 从目录展开该厂商全部上架 (模型, 能力)，逐行写入用户配置
        List<ProviderModel> catalog = providerModelService.listActiveModels(provider.getId(), null);

        List<UserLLMConfig> result = new ArrayList<>();
        for (ProviderModel pm : catalog) {
            // 协议与入口只取模型能力层事实，绝不 fallback 厂商默认；缺失即阻断整请求，不由下游临时猜测
            requireModelFact(pm);
            UserLLMConfig existing = findSelfConfig(userId, provider.getId(), pm.getModelName(), pm.getCapability());
            if (existing != null) {
                // 厂商级 Key 共用：重复配置厂商时统一刷新该模型能力行的 Key 与协议/地址快照，不动启停/生效标记
                existing.setApiKey(encryptedApiKey);
                existing.setApiBaseUrl(pm.getApiBaseUrl());
                existing.setProtocol(pm.getProtocol());
                userLLMConfigMapper.updateById(existing);
                result.add(existing);
            } else {
                UserLLMConfig config = new UserLLMConfig();
                config.setUserId(userId);
                config.setProviderId(provider.getId());
                config.setProviderType(provider.getProviderType());
                config.setApiKey(encryptedApiKey);
                config.setApiBaseUrl(pm.getApiBaseUrl());
                config.setProtocol(pm.getProtocol());
                config.setModelName(pm.getModelName());
                config.setCapability(pm.getCapability());
                config.setIsActive(true);
                config.setIsDefault(false);
                config.setIsSystemPreset(false);
                userLLMConfigMapper.insert(config);
                result.add(config);
            }
        }

        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
        result.forEach(config -> cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, config.getId()));
        // 审计：厂商级 Key 配置/更新（只记标识与影响行数，绝不记 Key 明文/密文）
        AuditLog.event("LLM_PROVIDER_SETUP", "userId={}, providerType={}, providerId={}, configRows={}",
                userId, provider.getProviderType(), provider.getId(), result.size());
        return result.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    /**
     * 模型启停（独立窗口）：capability 存在时只切换当前模型能力；capability 为空时
     * 兼容旧前端，按 (厂商, 模型) 批量切换该模型全部能力行。
     *
     * <p>只作用于用户自配行，LinkRag / 系统预设行不可通过该接口启停。若关闭的是当前能力
     * 用户默认配置，同时清除该默认标记，后续有效配置解析自然回退 LinkRag 系统默认。</p>
     */
    public void toggleModel(Long userId, ToggleModelRequest request) {
        if (LINKRAG_PROVIDER_TYPE.equals(request.getProviderType())) {
            throw new BusinessException(ErrorCode.SYSTEM_PROVIDER_READONLY);
        }
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());

        String capability = normalizeCapabilityIfPresent(request.getCapability());
        if (capability != null) {
            toggleModelCapability(userId, provider, request, capability);
            return;
        }

        userLLMConfigMapper.update(null,
                new LambdaUpdateWrapper<UserLLMConfig>()
                        .eq(UserLLMConfig::getUserId, userId)
                        .eq(UserLLMConfig::getProviderId, provider.getId())
                        .eq(UserLLMConfig::getModelName, request.getModelName())
                        .eq(UserLLMConfig::getIsSystemPreset, false)
                        .set(UserLLMConfig::getIsActive, request.getEnabled())
        );

        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    /**
     * 能力级启停：明确定位一条用户自配配置，不存在则返回 USER_CONFIG_NOT_FOUND。
     */
    private void toggleModelCapability(Long userId, SystemProvider provider, ToggleModelRequest request, String capability) {
        UserLLMConfig config = findSelfConfig(userId, provider.getId(), request.getModelName(), capability);
        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }

        config.setIsActive(request.getEnabled());
        if (Boolean.FALSE.equals(request.getEnabled()) && Boolean.TRUE.equals(config.getIsDefault())) {
            config.setIsDefault(false);
        }
        userLLMConfigMapper.updateById(config);

        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, config.getId());
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    @Override
    @Transactional
    /**
     * 按能力选生效模型（第二步）：把指定自配模型设为该能力生效。
     *
     * <p>校验顺序刻意为「先目录支持、再用户配置、再启停」：模型压根不支持该能力时返回
     * MODEL_NOT_SUPPORTED；支持但用户未配置返回 USER_CONFIG_NOT_FOUND；已配置但被关停返回
     * MODEL_DISABLED。设新生效前先清同能力其他行的生效，保证单用户单能力生效唯一。</p>
     */
    public void selectEffectiveModel(Long userId, SelectEffectiveModelRequest request) {
        String capability = normalizeCapability(request.getCapability());
        if (LINKRAG_PROVIDER_TYPE.equals(request.getProviderType())) {
            selectLinkRagEffective(userId, capability, request.getModelName());
            return;
        }
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());

        if (!providerModelService.isModelCapabilityActive(provider.getId(), request.getModelName(), capability)) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "模型不支持该能力");
        }

        UserLLMConfig config = findSelfConfig(userId, provider.getId(), request.getModelName(), capability);
        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(ErrorCode.MODEL_DISABLED);
        }

        clearOtherDefault(userId, capability, config.getId());
        config.setIsDefault(true);
        userLLMConfigMapper.updateById(config);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, config.getId());
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    /**
     * 前端把 LinkRag 当作只读配置厂商选择时，实际语义是清空该能力用户自配默认，
     * 让有效配置解析回退到 LinkRag 系统默认。
     */
    private void selectLinkRagEffective(Long userId, String capability, String modelName) {
        SystemPreset preset = systemPresetMapper.selectOne(new LambdaQueryWrapper<SystemPreset>()
                .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
                .eq(SystemPreset::getCapability, capability)
                .eq(SystemPreset::getModelName, modelName)
                .eq(SystemPreset::getIsDefault, true)
                .eq(SystemPreset::getIsActive, true)
                .last("LIMIT 1"));
        if (preset == null) {
            throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
        }
        clearDefaultConfig(userId, capability);
    }

    @Override
    @Transactional
    /**
     * 删除指定用户的自配 LLM 配置。系统兜底不在本表中维护。
     */
    public void deleteConfig(Long userId, Long configId) {
        UserLLMConfig config = getConfigOrThrow(userId, configId);
        userLLMConfigMapper.deleteById(configId);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
        AuditLog.event("LLM_CONFIG_DELETE", "userId={}, configId={}, providerType={}, modelName={}, capability={}",
                userId, configId, config.getProviderType(), config.getModelName(), config.getCapability());
    }

    @Override
    /**
     * 获取当前用户的自配默认 LLM 配置（CHAT 能力）。
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId) {
        return getDefaultConfig(userId, "CHAT");
    }

    @Override
    /**
     * 获取当前用户某个能力的自配默认 LLM 配置。
     *
     * <p>取 (用户, 能力, is_default=true, is_active=true, is_system_preset=false) 一行；
     * 系统兜底由 EffectiveLLMConfigService 处理。</p>
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        return userLLMConfigCacheService.getOrLoadAll(userId, () -> loadAllConfigDTOs(userId)).stream()
                .filter(dto -> Objects.equals(normalizedCapability, dto.getCapability()))
                .filter(dto -> Boolean.TRUE.equals(dto.getIsDefault()))
                .filter(dto -> Boolean.TRUE.equals(dto.getIsActive()))
                .filter(dto -> !Boolean.TRUE.equals(dto.getIsSystemPreset()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_DEFAULT_CONFIG));
    }

    @Override
    @Transactional
    /**
     * 将当前用户的一条自配配置设为某能力生效。
     */
    public void setDefaultConfig(Long userId, Long configId, String capability) {
        String normalizedCapability = normalizeCapability(capability);
        UserLLMConfig config = getConfigOrThrow(userId, configId);

        if (!normalizedCapability.equals(config.getCapability())) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY, "配置不具备该模型能力");
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(ErrorCode.USER_CONFIG_DISABLED);
        }

        clearOtherDefault(userId, normalizedCapability, configId);
        config.setIsDefault(true);
        userLLMConfigMapper.updateById(config);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    @Override
    @Transactional
    public void clearDefaultConfig(Long userId, String capability) {
        String normalizedCapability = normalizeCapability(capability);
        userLLMConfigMapper.update(null,
                new LambdaUpdateWrapper<UserLLMConfig>()
                        .eq(UserLLMConfig::getUserId, userId)
                        .eq(UserLLMConfig::getCapability, normalizedCapability)
                        .eq(UserLLMConfig::getIsSystemPreset, false)
                        .set(UserLLMConfig::getIsDefault, false)
        );
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    /**
     * 查询某 (用户, 厂商, 模型, 能力) 的自配行（排除预设行）。
     * 预设行与自配行可同键共存，故此处显式只取 is_system_preset=false。
     */
    private UserLLMConfig findSelfConfig(Long userId, Long providerId, String modelName, String capability) {
        return userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getProviderId, providerId)
                .eq(UserLLMConfig::getModelName, modelName)
                .eq(UserLLMConfig::getCapability, capability)
                .eq(UserLLMConfig::getIsSystemPreset, false)
        );
    }

    /**
     * 查询当前用户的配置，不存在时抛出异常。
     */
    private UserLLMConfig getConfigOrThrow(Long userId, Long configId) {
        UserLLMConfig config = userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getId, configId)
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getIsSystemPreset, false)
        );

        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }
        return config;
    }

    /**
     * 清理当前用户指定能力下、除指定配置外的自配生效标记，保证单用户单能力自配生效唯一。
     */
    private void clearOtherDefault(Long userId, String capability, Long excludeConfigId) {
        userLLMConfigMapper.update(null,
            new LambdaUpdateWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, capability)
                .eq(UserLLMConfig::getIsSystemPreset, false)
                .ne(UserLLMConfig::getId, excludeConfigId)
                .set(UserLLMConfig::getIsDefault, false)
        );
    }

    /**
     * 能力必填校验并归一化为大写。
     */
    private String normalizeCapability(String capability) {
        llmCapabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }

    /**
     * 归一化并校验能力值，为空时返回 null 表示不过滤。
     */
    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        return normalizeCapability(capability);
    }

    /**
     * 校验模型能力事实完整：缺协议或入口时阻断展开，避免生成下游无法使用的快照。
     */
    private void requireModelFact(ProviderModel pm) {
        if (!StringUtils.hasText(pm.getProtocol()) || !StringUtils.hasText(pm.getApiBaseUrl())) {
            throw new BusinessException(ErrorCode.MODEL_CONFIG_INCOMPLETE);
        }
    }

    /**
     * 将用户 LLM 配置实体转换为 DTO，并对 API Key 做脱敏（含预设 Key 不可见）。
     */
    private UserLLMConfigDTO toDTO(UserLLMConfig config) {
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
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }

    private UserLLMConfigDTO toLinkRagDTO(SystemPreset preset, boolean hasActiveUserDefault) {
        UserLLMConfigDTO dto = new UserLLMConfigDTO();
        dto.setId(preset.getId());
        dto.setProviderType(preset.getProviderType());
        dto.setModelName(preset.getModelName());
        dto.setCapability(preset.getCapability());
        dto.setApiKeyMasked(apiKeyEncryptService.maskApiKey(preset.getApiKey()));
        dto.setApiBaseUrl(preset.getApiBaseUrl());
        dto.setProtocol(preset.getProtocol());
        dto.setIsActive(true);
        dto.setIsDefault(!hasActiveUserDefault);
        dto.setIsSystemPreset(true);
        dto.setIsEditable(false);
        dto.setCreatedAt(preset.getCreatedAt());
        dto.setUpdatedAt(preset.getUpdatedAt());
        return dto;
    }

    private boolean hasActiveUserDefault(List<UserLLMConfigDTO> userConfigs, String capability) {
        return userConfigs.stream()
                .anyMatch(dto -> capability.equals(dto.getCapability())
                        && Boolean.TRUE.equals(dto.getIsDefault())
                        && Boolean.TRUE.equals(dto.getIsActive()));
    }
}
