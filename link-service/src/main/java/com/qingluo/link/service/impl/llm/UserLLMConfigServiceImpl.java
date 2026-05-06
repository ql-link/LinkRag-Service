package com.qingluo.link.service.impl.llm;

import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.ConflictException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.UserLLMConfigService;
import com.qingluo.link.service.cache.UserLLMConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户 LLM 配置服务实现
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemProviderService systemProviderService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final UserLLMConfigCacheService userLLMConfigCacheService;

    @Override
    public List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, String capability, Boolean isActive) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        List<UserLLMConfig> configs =
                userLLMConfigMapper.selectByUserConditions(userId, providerType, normalizedCapability, isActive);
        return configs.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public List<UserLLMConfigDTO> createConfig(Long userId, CreateConfigRequest request) {
        var provider = systemProviderService.getActiveByProviderType(request.getProviderType());
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
            userLLMConfigCacheService.evictDefaultMap(userId);
        }

        configs.forEach(config -> userLLMConfigCacheService.evictConfig(config.getId()));
        return configs.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
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
        userLLMConfigCacheService.evictConfig(configId);
        userLLMConfigCacheService.evictDefaultMap(userId);
    }

    @Override
    @Transactional
    public void deleteConfig(Long userId, Long configId) {
        UserLLMConfig config = getConfigOrThrow(userId, configId);
        userLLMConfigMapper.deleteById(configId);
        userLLMConfigCacheService.evictConfig(configId);
        userLLMConfigCacheService.evictDefaultMap(userId);
    }

    @Override
    public UserLLMConfigDTO getDefaultConfig(Long userId) {
        return getDefaultConfig(userId, "CHAT");
    }

    @Override
    public UserLLMConfigDTO getDefaultConfig(Long userId, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        Map<String, Long> defaultMap = userLLMConfigCacheService.getDefaultConfigIdMapOrLoad(
                userId,
                () -> userLLMConfigMapper.selectDefaultsByUserId(userId).stream()
                        .collect(Collectors.toMap(
                                UserLLMConfig::getCapability,
                                UserLLMConfig::getId,
                                (first, ignored) -> first
                        ))
        );
        Long configId = defaultMap.get(normalizedCapability);
        if (configId == null) {
            throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
        }
        UserLLMConfig config = userLLMConfigCacheService.getConfigOrLoad(
                configId,
                () -> userLLMConfigMapper.selectByIdAndUserId(configId, userId)
        );
        if (config == null) {
            userLLMConfigCacheService.evictDefaultMap(userId);
            throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
        }
        return toDTO(config);
    }

    @Override
    @Transactional
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
        userLLMConfigCacheService.evictConfig(configId);
        userLLMConfigCacheService.evictDefaultMap(userId);
    }

    private UserLLMConfig getConfigOrThrow(Long userId, Long configId) {
        UserLLMConfig config = userLLMConfigMapper.selectByIdAndUserId(configId, userId);
        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }
        return config;
    }

    private void clearOtherDefault(Long userId, String capability, Long excludeConfigId) {
        userLLMConfigMapper.clearDefaultByUserIdAndCapability(userId, capability, excludeConfigId);
    }

    private UserLLMConfig buildConfig(Long userId,
                                      CreateConfigRequest request,
                                      com.qingluo.link.model.dto.entity.SystemProvider provider,
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

    private void validateEntryCapability(CreateConfigRequest request, List<String> modelCapabilities) {
        if (!StringUtils.hasText(request.getCapability())) {
            return;
        }
        String entryCapability = normalizeCapabilityIfPresent(request.getCapability());
        if (!modelCapabilities.contains(entryCapability)) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "模型不支持指定能力");
        }
    }

    private void ensureNotDuplicate(Long userId, Long providerId, String modelName, String capability) {
        long count = userLLMConfigMapper.countByUserModelCapability(userId, providerId, modelName, capability);
        if (count > 0) {
            throw ConflictException.duplicateUserConfig();
        }
    }

    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }

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
