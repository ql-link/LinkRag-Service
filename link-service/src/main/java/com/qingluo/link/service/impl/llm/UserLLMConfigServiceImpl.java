package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.UserLLMConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户 LLM 配置服务实现
 */
@Service
@RequiredArgsConstructor
public class UserLLMConfigServiceImpl implements UserLLMConfigService {

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemProviderService systemProviderService;
    private final ApiKeyEncryptService apiKeyEncryptService;
    private final CacheConsistencyService cacheConsistencyService;

    @Override
    /**
     * 按条件查询用户 LLM 配置列表。
     */
    public List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, Boolean isActive) {
        LambdaQueryWrapper<UserLLMConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLLMConfig::getUserId, userId);

        if (providerType != null) {
            wrapper.eq(UserLLMConfig::getProviderType, providerType);
        }
        if (isActive != null) {
            wrapper.eq(UserLLMConfig::getIsActive, isActive);
        }

        List<UserLLMConfig> configs = userLLMConfigMapper.selectList(wrapper);

        return configs.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    /**
     * 创建新的用户 LLM 配置。
     */
    public UserLLMConfigDTO createConfig(Long userId, CreateConfigRequest request) {
        // 获取系统厂商信息
        var provider = systemProviderService.getByProviderType(request.getProviderType());

        // 加密 API Key
        String encryptedApiKey = apiKeyEncryptService.encrypt(request.getApiKey());

        UserLLMConfig config = new UserLLMConfig();
        config.setUserId(userId);
        config.setProviderId(provider.getId());
        config.setProviderType(provider.getProviderType());
        config.setProviderName(provider.getProviderName());
        config.setConfigName(request.getConfigName());
        config.setApiKey(encryptedApiKey);
        config.setModelName(request.getModelName());
        config.setPriority(request.getPriority() != null ? request.getPriority() : 50);
        config.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        config.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 60000);
        config.setMaxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3);
        config.setStreamEnabled(request.getStreamEnabled() != null ? request.getStreamEnabled() : true);
        config.setIsActive(true);

        userLLMConfigMapper.insert(config);

        // 如果设置为默认，取消其他默认
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearOtherDefault(userId, config.getId());
        }

        return toDTO(config);
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
                clearOtherDefault(userId, configId);
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
        UserLLMConfig config = getConfigOrThrow(userId, configId);
        userLLMConfigMapper.deleteById(configId);
        cacheConsistencyService.evict(CacheEvictTarget.LLM_CONFIG, configId);
        cacheConsistencyService.evict(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, userId);
    }

    @Override
    /**
     * 获取当前用户的默认 LLM 配置。
     */
    public UserLLMConfigDTO getDefaultConfig(Long userId) {
        UserLLMConfig config = userLLMConfigMapper.selectOne(
            new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getIsDefault, true)
                .eq(UserLLMConfig::getIsActive, true)
        );

        if (config == null) {
            throw NotFoundException.userConfigNotFound();
        }

        return toDTO(config);
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
     * 清理当前用户除指定配置外的默认标记。
     */
    private void clearOtherDefault(Long userId, Long excludeConfigId) {
        userLLMConfigMapper.update(null,
            new LambdaUpdateWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .ne(UserLLMConfig::getId, excludeConfigId)
                .set(UserLLMConfig::getIsDefault, false)
        );
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
