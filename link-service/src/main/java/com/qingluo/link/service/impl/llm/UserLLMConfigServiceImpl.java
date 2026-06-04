package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.ConflictException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.UserLLMConfigService;
import com.qingluo.link.service.cache.RagCacheSyncNotifier;
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
 * <p>配置按用户选择的能力维度存储（一个能力一行），默认配置按能力维度维护；
 * 缓存失效统一走 {@link CacheConsistencyService} 的 CDC 一致性框架。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private static final Long SYSTEM_PRESET_USER_ID = 0L;

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemProviderService systemProviderService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;
    private final RagCacheSyncNotifier ragCacheSyncNotifier;

    @Override
    /**
     * 按条件查询用户 LLM 配置列表，支持按能力过滤。
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

        List<UserLLMConfigDTO> configs = new ArrayList<>(
                userLLMConfigMapper.selectList(wrapper).stream().map(this::toDTO).toList());
        if (!Boolean.FALSE.equals(isActive)) {
            configs.addAll(getSystemPresetConfigs(providerType, normalizedCapability).stream()
                    .map(this::toDTO)
                    .toList());
        }
        return configs;
    }

    @Override
    @Transactional
    /**
     * 创建用户 LLM 配置：按用户选择的能力写入单条配置。
     */
    public UserLLMConfigDTO createConfig(Long userId, CreateConfigRequest request) {
        SystemProvider provider = resolveProvider(request);
        String capability = normalizeRequiredCapability(request.getCapability());
        llmCapabilityService.ensureProviderSupports(provider, capability);

        String encryptedApiKey = apiKeyEncryptService.encrypt(request.getApiKey());
        UserLLMConfig config = buildConfig(userId, request, provider, encryptedApiKey, capability);

        ensureNotDuplicate(userId, provider.getId(), request.getModelName(), capability);
        userLLMConfigMapper.insert(config);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearOtherDefault(userId, capability, config.getId());
        }

        evictUserConfigCaches(userId, config.getId());
        ragCacheSyncNotifier.notifyRefresh(userId, config.getId());
        return toDTO(config);
    }

    @Override
    @Transactional
    /**
     * 更新指定用户的 LLM 配置。
     */
    public void updateConfig(Long userId, Long configId, UpdateConfigRequest request) {
        UserLLMConfig config = getMutableConfigOrThrow(userId, configId);

        if (request.getApiKey() != null) {
            config.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
        }
        if (request.getPriority() != null) {
            config.setPriority(request.getPriority());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getIsDefault() != null) {
            config.setIsDefault(request.getIsDefault());
            if (Boolean.TRUE.equals(request.getIsDefault())) {
                clearOtherDefault(userId, config.getCapability(), configId);
            }
        }
        if (request.getTimeoutMs() != null) {
            config.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getMaxRetries() != null) {
            config.setMaxRetries(request.getMaxRetries());
        }
        if (request.getStreamEnabled() != null) {
            config.setStreamEnabled(request.getStreamEnabled());
        }
        if (request.getCustomApiBaseUrl() != null) {
            config.setCustomApiBaseUrl(request.getCustomApiBaseUrl());
        }
        if (request.getExtraConfig() != null) {
            config.setExtraConfig(request.getExtraConfig());
        }

        userLLMConfigMapper.updateById(config);
        evictUserConfigCaches(userId, configId);
        ragCacheSyncNotifier.notifyRefresh(userId, configId);
    }

    @Override
    @Transactional
    /**
     * 删除指定用户的 LLM 配置。
     */
    public void deleteConfig(Long userId, Long configId) {
        getMutableConfigOrThrow(userId, configId);
        userLLMConfigMapper.deleteById(configId);
        evictUserConfigCaches(userId, configId);
        ragCacheSyncNotifier.notifyInvalidate(userId, configId);
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
     * 获取当前用户某个能力的默认 LLM 配置。
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId, String capability) {
        String normalizedCapability = normalizeRequiredCapability(capability);
        UserLLMConfig config = userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, normalizedCapability)
                .eq(UserLLMConfig::getIsDefault, true)
                .eq(UserLLMConfig::getIsActive, true)
        );

        if (config == null) {
            config = getSystemPresetDefaultOrNull(normalizedCapability);
        }
        if (config == null) {
            throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
        }
        return toDTO(config);
    }

    @Override
    @Transactional
    /**
     * 将当前用户的一条配置设置为指定能力的默认配置。
     */
    public void setDefaultConfig(Long userId, Long configId, String capability) {
        String normalizedCapability = normalizeRequiredCapability(capability);
        UserLLMConfig config = getConfigByIdOrThrow(configId);

        if (!userId.equals(config.getUserId()) && !isSystemPreset(config)) {
            throw NotFoundException.userConfigNotFound();
        }

        if (!normalizedCapability.equals(config.getCapability())) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY, "配置不具备该模型能力");
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(ErrorCode.USER_CONFIG_DISABLED);
        }

        if (isSystemPreset(config)) {
            clearDefault(userId, normalizedCapability);
            evictUserConfigCaches(userId, configId);
            ragCacheSyncNotifier.notifyRefresh(userId, configId);
            return;
        }

        clearOtherDefault(userId, normalizedCapability, configId);
        config.setIsDefault(true);
        userLLMConfigMapper.updateById(config);
        evictUserConfigCaches(userId, configId);
        ragCacheSyncNotifier.notifyRefresh(userId, configId);
    }

    /**
     * 根据 providerId 或 providerType 定位启用厂商，二者同时传入时要求指向同一厂商。
     */
    private SystemProvider resolveProvider(CreateConfigRequest request) {
        SystemProvider provider;
        if (request.getProviderId() != null) {
            provider = systemProviderService.getActiveByProviderId(request.getProviderId());
            if (StringUtils.hasText(request.getProviderType())
                    && !request.getProviderType().equals(provider.getProviderType())) {
                throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "providerId 与 providerType 不匹配");
            }
            return provider;
        }
        return systemProviderService.getActiveByProviderType(request.getProviderType());
    }

    /**
     * 查询可变更配置。系统预设配置不可由普通用户修改或删除，其他用户配置按不存在处理。
     */
    private UserLLMConfig getMutableConfigOrThrow(Long userId, Long configId) {
        UserLLMConfig config = getConfigByIdOrThrow(configId);
        if (isSystemPreset(config)) {
            throw new BusinessException(ErrorCode.SYSTEM_PRESET_READONLY);
        }
        if (!userId.equals(config.getUserId())) {
            throw NotFoundException.userConfigNotFound();
        }
        return config;
    }

    /**
     * 按 ID 查询配置，不限定用户，用于区分系统预设与其他用户配置。
     */
    private UserLLMConfig getConfigByIdOrThrow(Long configId) {
        UserLLMConfig config = userLLMConfigMapper.selectById(configId);
        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }
        return config;
    }

    /**
     * 清理当前用户指定能力下、除指定配置外的默认标记。
     */
    private void clearOtherDefault(Long userId, String capability, Long excludeConfigId) {
        userLLMConfigMapper.update(null,
            new UpdateWrapper<UserLLMConfig>()
                .eq("user_id", userId)
                .eq("capability", capability)
                .ne("id", excludeConfigId)
                .set("is_default", false)
        );
    }

    /**
     * 清理当前用户指定能力下的所有个人默认配置，用于切回系统预设配置。
     */
    private void clearDefault(Long userId, String capability) {
        userLLMConfigMapper.update(null,
            new UpdateWrapper<UserLLMConfig>()
                .eq("user_id", userId)
                .eq("capability", capability)
                .set("is_default", false)
        );
    }

    /**
     * 根据请求与能力构建一条待插入的用户配置实体。
     */
    private UserLLMConfig buildConfig(Long userId,
                                      CreateConfigRequest request,
                                      SystemProvider provider,
                                      String encryptedApiKey,
                                      String capability) {
        UserLLMConfig config = new UserLLMConfig();
        config.setUserId(userId);
        config.setProviderId(provider.getId());
        config.setProviderType(provider.getProviderType());
        config.setProviderName(provider.getProviderName());
        config.setConfigName(request.getConfigName());
        config.setApiKey(encryptedApiKey);
        config.setCustomApiBaseUrl(request.getCustomApiBaseUrl());
        config.setModelName(request.getModelName());
        config.setCapability(capability);
        config.setPriority(request.getPriority() != null ? request.getPriority() : 50);
        config.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        config.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 60000);
        config.setMaxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3);
        config.setStreamEnabled(request.getStreamEnabled() != null ? request.getStreamEnabled() : true);
        config.setIsActive(true);
        config.setExtraConfig(request.getExtraConfig());
        return config;
    }

    /**
     * 校验同一用户、厂商、模型、能力下不存在重复配置。
     */
    private void ensureNotDuplicate(Long userId, Long providerId, String modelName, String capability) {
        long count = userLLMConfigMapper.selectCount(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getProviderId, providerId)
                .eq(UserLLMConfig::getModelName, modelName)
                .eq(UserLLMConfig::getCapability, capability)
        );
        if (count > 0) {
            throw ConflictException.duplicateUserConfig();
        }
    }

    /**
     * 归一化并校验能力值，为空时返回 null 表示不过滤。
     */
    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }

    /**
     * 归一化并校验必填能力值。
     */
    private String normalizeRequiredCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY, "能力不能为空");
        }
        return normalizeCapabilityIfPresent(capability);
    }

    /**
     * 清理 Java 本地缓存，并保持默认配置查询缓存同步失效。
     */
    private void evictUserConfigCaches(Long userId, Long configId) {
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    private List<UserLLMConfig> getSystemPresetConfigs(String providerType, String capability) {
        LambdaQueryWrapper<UserLLMConfig> wrapper = new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, SYSTEM_PRESET_USER_ID)
                .eq(UserLLMConfig::getIsActive, true)
                .eq(UserLLMConfig::getIsDefault, true);
        if (StringUtils.hasText(providerType)) {
            wrapper.eq(UserLLMConfig::getProviderType, providerType);
        }
        if (StringUtils.hasText(capability)) {
            wrapper.eq(UserLLMConfig::getCapability, capability);
        }
        return userLLMConfigMapper.selectList(wrapper);
    }

    private UserLLMConfig getSystemPresetDefaultOrNull(String capability) {
        return userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, SYSTEM_PRESET_USER_ID)
                .eq(UserLLMConfig::getCapability, capability)
                .eq(UserLLMConfig::getIsDefault, true)
                .eq(UserLLMConfig::getIsActive, true)
        );
    }

    private boolean isSystemPreset(UserLLMConfig config) {
        return config != null && SYSTEM_PRESET_USER_ID.equals(config.getUserId());
    }

    /**
     * 将用户 LLM 配置实体转换为 DTO，并对 API Key 做脱敏。
     */
    private UserLLMConfigDTO toDTO(UserLLMConfig config) {
        UserLLMConfigDTO dto = new UserLLMConfigDTO();
        dto.setId(config.getId());
        dto.setConfigName(config.getConfigName());
        dto.setProviderType(config.getProviderType());
        dto.setProviderName(config.getProviderName());
        boolean systemPreset = isSystemPreset(config);
        dto.setModelName(systemPreset ? null : config.getModelName());
        dto.setCapability(config.getCapability());
        dto.setApiKeyMasked(systemPreset ? null : apiKeyEncryptService.maskApiKey(config.getApiKey()));
        dto.setCustomApiBaseUrl(systemPreset ? null : config.getCustomApiBaseUrl());
        dto.setPriority(config.getPriority());
        dto.setIsActive(config.getIsActive());
        dto.setIsDefault(config.getIsDefault());
        dto.setSystemPreset(systemPreset);
        dto.setEditable(!systemPreset);
        dto.setSelectable(Boolean.TRUE.equals(config.getIsActive()));
        dto.setTimeoutMs(config.getTimeoutMs());
        dto.setMaxRetries(config.getMaxRetries());
        dto.setStreamEnabled(config.getStreamEnabled());
        dto.setExtraConfig(systemPreset ? null : config.getExtraConfig());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }
}
