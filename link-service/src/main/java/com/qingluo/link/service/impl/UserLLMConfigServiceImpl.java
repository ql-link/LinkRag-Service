package com.qingluo.link.service.impl;

import com.qingluo.link.core.dto.request.CreateConfigRequest;
import com.qingluo.link.core.dto.request.UpdateConfigRequest;
import com.qingluo.link.core.dto.response.UserLLMConfigDTO;
import com.qingluo.link.core.enums.ErrorCode;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.model.entity.SystemProvider;
import com.qingluo.link.model.entity.UserLLMConfig;
import com.qingluo.link.service.UserLLMConfigService;
import com.qingluo.link.service.mapper.SystemProviderMapper;
import com.qingluo.link.service.mapper.UserLLMConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户 LLM 配置服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private final UserLLMConfigMapper configMapper;
    private final SystemProviderMapper providerMapper;

    @Override
    public List<UserLLMConfigDTO> listUserConfigs(String userId) {
        List<UserLLMConfig> configs = configMapper.selectByUserId(userId);
        return configs.stream().map(this::toDTO).toList();
    }

    @Override
    public UserLLMConfigDTO getUserConfig(String userId, String configId) {
        UserLLMConfig config = configMapper.selectById(configId);
        if (config == null || !config.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "配置不存在");
        }
        return toDTO(config);
    }

    @Override
    public UserLLMConfigDTO getDefaultConfig(String userId) {
        UserLLMConfig config = configMapper.selectDefaultByUserId(userId);
        if (config == null) {
            throw new NotFoundException(ErrorCode.NO_DEFAULT_CONFIG, "用户没有设置默认配置");
        }
        return toDTO(config);
    }

    @Override
    @Transactional
    public UserLLMConfigDTO createUserConfig(String userId, CreateConfigRequest request) {
        // 验证厂商存在
        SystemProvider provider = providerMapper.selectByType(request.getProviderType());
        if (provider == null || !provider.getIsActive()) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "厂商不存在或已禁用");
        }

        // 如果设为默认，先取消其他默认
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultConfig(userId);
        }

        UserLLMConfig config = UserLLMConfig.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .providerId(provider.getId())
            .providerType(provider.getProviderType())
            .providerName(provider.getProviderName())
            .configName(request.getConfigName())
            .apiKey(request.getApiKey()) // 实际应加密
            .modelName(request.getModelName())
            .priority(request.getPriority() != null ? request.getPriority() : 50)
            .isActive(true)
            .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
            .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 60000)
            .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
            .streamEnabled(request.getStreamEnabled() != null ? request.getStreamEnabled() : true)
            .capabilities(provider.getSupportedModels())
            .extraConfig(request.getExtraConfig())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        configMapper.insert(config);
        log.info("创建用户配置成功: userId={}, configId={}", userId, config.getId());

        return toDTO(config);
    }

    @Override
    @Transactional
    public UserLLMConfigDTO updateUserConfig(String userId, String configId, UpdateConfigRequest request) {
        UserLLMConfig config = configMapper.selectById(configId);
        if (config == null || !config.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "配置不存在");
        }

        // 如果设为默认，先取消其他默认
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultConfig(userId);
        }

        // 部分更新
        if (request.getApiKey() != null) config.setApiKey(request.getApiKey());
        if (request.getCustomApiBaseUrl() != null) config.setCustomApiBaseUrl(request.getCustomApiBaseUrl());
        if (request.getPriority() != null) config.setPriority(request.getPriority());
        if (request.getIsActive() != null) config.setIsActive(request.getIsActive());
        if (request.getIsDefault() != null) config.setIsDefault(request.getIsDefault());
        if (request.getTimeoutMs() != null) config.setTimeoutMs(request.getTimeoutMs());
        if (request.getMaxRetries() != null) config.setMaxRetries(request.getMaxRetries());
        if (request.getStreamEnabled() != null) config.setStreamEnabled(request.getStreamEnabled());
        if (request.getExtraConfig() != null) config.setExtraConfig(request.getExtraConfig());
        config.setUpdatedAt(LocalDateTime.now());

        configMapper.updateById(config);

        return toDTO(config);
    }

    @Override
    public void deleteUserConfig(String userId, String configId) {
        UserLLMConfig config = configMapper.selectById(configId);
        if (config == null || !config.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "配置不存在");
        }
        configMapper.deleteById(configId);
    }

    @Override
    @Transactional
    public void setDefaultConfig(String userId, String configId) {
        UserLLMConfig config = configMapper.selectById(configId);
        if (config == null || !config.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "配置不存在");
        }
        clearDefaultConfig(userId);
        config.setIsDefault(true);
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
    }

    private void clearDefaultConfig(String userId) {
        List<UserLLMConfig> configs = configMapper.selectByUserId(userId);
        for (UserLLMConfig c : configs) {
            if (Boolean.TRUE.equals(c.getIsDefault())) {
                c.setIsDefault(false);
                c.setUpdatedAt(LocalDateTime.now());
                configMapper.updateById(c);
            }
        }
    }

    private UserLLMConfigDTO toDTO(UserLLMConfig config) {
        return UserLLMConfigDTO.builder()
            .id(config.getId())
            .configName(config.getConfigName())
            .providerType(config.getProviderType())
            .providerName(config.getProviderName())
            .modelName(config.getModelName())
            .capabilities(config.getCapabilities())
            .apiKeyMasked(maskApiKey(config.getApiKey()))
            .customApiBaseUrl(config.getCustomApiBaseUrl())
            .priority(config.getPriority())
            .isActive(config.getIsActive())
            .isDefault(config.getIsDefault())
            .timeoutMs(config.getTimeoutMs())
            .maxRetries(config.getMaxRetries())
            .streamEnabled(config.getStreamEnabled())
            .extraConfig(config.getExtraConfig())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}