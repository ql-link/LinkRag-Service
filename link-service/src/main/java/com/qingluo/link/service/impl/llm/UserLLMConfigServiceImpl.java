package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 用户 LLM 配置服务实现。
 *
 * <p>配置按模型支持的能力展开存储（一个能力一行），默认配置按能力维度维护；
 * 缓存失效统一走 {@link CacheConsistencyService} 的 CDC 一致性框架。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemProviderService systemProviderService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;

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

        return userLLMConfigMapper.selectList(wrapper).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    /**
     * 创建用户 LLM 配置：按模型支持的全部能力展开为多条配置。
     */
    public List<UserLLMConfigDTO> createConfig(Long userId, CreateConfigRequest request) {
        SystemProvider provider = systemProviderService.getActiveByProviderType(request.getProviderType());
        List<String> capabilities = llmCapabilityService.getModelCapabilities(provider, request.getModelName());
        validateEntryCapability(request, capabilities);

        String encryptedApiKey = apiKeyEncryptService.encrypt(request.getApiKey());
        List<UserLLMConfig> configs = capabilities.stream()
                .map(capability -> buildConfig(userId, request, provider, encryptedApiKey, capability))
                .toList();

        for (UserLLMConfig config : configs) {
            ensureNotDuplicate(userId, provider.getId(), request.getModelName(), config.getCapability());
            userLLMConfigMapper.insert(config);
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            for (UserLLMConfig config : configs) {
                clearOtherDefault(userId, config.getCapability(), config.getId());
            }
            cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
        }

        configs.forEach(config -> cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, config.getId()));
        return configs.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    /**
     * 更新指定用户的 LLM 配置。
     */
    public void updateConfig(Long userId, Long configId, UpdateConfigRequest request) {
        UserLLMConfig config = getConfigOrThrow(userId, configId);

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
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    @Override
    @Transactional
    /**
     * 删除指定用户的 LLM 配置。
     */
    public void deleteConfig(Long userId, Long configId) {
        getConfigOrThrow(userId, configId);
        userLLMConfigMapper.deleteById(configId);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
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
     * 将当前用户的一条配置设置为指定能力的默认配置。
     */
    public void setDefaultConfig(Long userId, Long configId, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
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
     * 清理当前用户指定能力下、除指定配置外的默认标记。
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
     * 若请求显式指定了能力，校验该能力在模型支持范围内。
     */
    private void validateEntryCapability(CreateConfigRequest request, List<String> modelCapabilities) {
        if (!StringUtils.hasText(request.getCapability())) {
            return;
        }
        String entryCapability = normalizeCapabilityIfPresent(request.getCapability());
        if (!modelCapabilities.contains(entryCapability)) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "模型不支持指定能力");
        }
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
     * 将用户 LLM 配置实体转换为 DTO，并对 API Key 做脱敏。
     */
    private UserLLMConfigDTO toDTO(UserLLMConfig config) {
        UserLLMConfigDTO dto = new UserLLMConfigDTO();
        dto.setId(config.getId());
        dto.setConfigName(config.getConfigName());
        dto.setProviderType(config.getProviderType());
        dto.setProviderName(config.getProviderName());
        dto.setModelName(config.getModelName());
        dto.setCapability(config.getCapability());
        dto.setApiKeyMasked(apiKeyEncryptService.maskApiKey(config.getApiKey()));
        dto.setCustomApiBaseUrl(config.getCustomApiBaseUrl());
        dto.setPriority(config.getPriority());
        dto.setIsActive(config.getIsActive());
        dto.setIsDefault(config.getIsDefault());
        dto.setTimeoutMs(config.getTimeoutMs());
        dto.setMaxRetries(config.getMaxRetries());
        dto.setStreamEnabled(config.getStreamEnabled());
        dto.setExtraConfig(config.getExtraConfig());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }
}
