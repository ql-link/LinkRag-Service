package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.log.AuditLog;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用户 LLM 配置服务实现。
 *
 * <p>用户配置表是下游唯一生效源，按能力多行存储；系统预设行（is_system_preset=true）
 * 与用户自配行（is_system_preset=false）共存于本表，靠 is_default 表达「某能力当前生效哪一条」。
 * Key 为厂商级——同厂商多行共用同一个加密 Key。缓存失效统一走 {@link CacheConsistencyService}。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemProviderService systemProviderService;
    private final ProviderModelService providerModelService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;

    @Override
    /**
     * 按条件查询用户 LLM 配置列表（含预设行），支持按能力过滤。
     */
    public List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, String capability, Boolean isActive) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);

        LambdaQueryWrapper<UserLLMConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLLMConfig::getUserId, userId);
        if (providerType != null) {
            wrapper.eq(UserLLMConfig::getProviderType, providerType);
        }
        if (normalizedCapability != null) {
            wrapper.eq(UserLLMConfig::getCapability, normalizedCapability);
        }
        if (isActive != null) {
            wrapper.eq(UserLLMConfig::getIsActive, isActive);
        }

        return userLLMConfigMapper.selectList(wrapper).stream().map(this::toDTO).toList();
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
     * 模型启停（独立窗口）：按 (厂商, 模型) 批量切换该模型全部能力行的启用状态。
     * 只作用于自配行，预设行作为常备备选不被启停。关停后该模型既退出按能力选生效的候选，
     * 又因 is_active=false 不会被 getDefaultConfig 取为生效。
     */
    public void toggleModel(Long userId, ToggleModelRequest request) {
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());

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

    @Override
    @Transactional
    /**
     * 删除指定用户的 LLM 配置；系统预设行只读，删除被拒（PRESET_READONLY）。
     */
    public void deleteConfig(Long userId, Long configId) {
        UserLLMConfig config = getConfigOrThrow(userId, configId);
        if (Boolean.TRUE.equals(config.getIsSystemPreset())) {
            throw new BusinessException(ErrorCode.PRESET_READONLY);
        }
        userLLMConfigMapper.deleteById(configId);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
        AuditLog.event("LLM_CONFIG_DELETE", "userId={}, configId={}, providerType={}, modelName={}, capability={}",
                userId, configId, config.getProviderType(), config.getModelName(), config.getCapability());
    }

    @Override
    /**
     * 获取当前用户的默认 LLM 配置（CHAT 能力）。
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId) {
        return getDefaultConfig(userId, "CHAT");
    }

    @Override
    /**
     * 获取当前用户某个能力的生效 LLM 配置。
     *
     * <p>取 (用户, 能力, is_default=true, is_active=true) 一行：有自配生效命中自配；
     * 两步中间态（已配厂商未选生效）回退到注册写入的系统预设；都没有则报 NO_DEFAULT_CONFIG。</p>
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        UserLLMConfig config = userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, normalizedCapability)
                .eq(UserLLMConfig::getIsDefault, true)
                .eq(UserLLMConfig::getIsActive, true)
        );

        if (config == null) {
            throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
        }
        return toDTO(config);
    }

    @Override
    @Transactional
    /**
     * 将当前用户的一条配置设为某能力生效（含按能力切换到/切回系统预设）。
     * 预设行 is_active 恒为真，可被放行切换生效。
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
        );

        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }
        return config;
    }

    /**
     * 清理当前用户指定能力下、除指定配置外的生效标记（预设与自配一并清，保证单能力生效唯一）。
     */
    private void clearOtherDefault(Long userId, String capability, Long excludeConfigId) {
        userLLMConfigMapper.update(null,
            new LambdaUpdateWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, capability)
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
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }
}
