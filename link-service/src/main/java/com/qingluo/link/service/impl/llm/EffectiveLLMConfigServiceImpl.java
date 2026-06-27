package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.response.EffectiveLLMConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.EffectiveLLMConfigService;
import com.qingluo.link.service.LLMCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 有效 LLM 配置解析实现。
 *
 * <p>用户自己的配置优先；没有用户自配默认时，回退到 LinkRag 系统预设。
 * Python 执行端通过返回的 source + configId 定位读取哪张配置表。</p>
 */
@Service
@RequiredArgsConstructor
public class EffectiveLLMConfigServiceImpl implements EffectiveLLMConfigService {

    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String LINKRAG_PROVIDER_TYPE = "linkrag";

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemPresetMapper systemPresetMapper;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;

    @Override
    public EffectiveLLMConfigDTO getEffectiveConfig(Long userId, String capability) {
        String normalizedCapability = normalizeCapability(capability);
        UserLLMConfig userConfig = userLLMConfigMapper.selectOne(new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, normalizedCapability)
                .eq(UserLLMConfig::getIsDefault, true)
                .eq(UserLLMConfig::getIsActive, true)
                .eq(UserLLMConfig::getIsSystemPreset, false)
                .last("LIMIT 1"));
        if (userConfig != null) {
            return fromUser(userConfig);
        }

        SystemPreset systemPreset = systemPresetMapper.selectOne(new LambdaQueryWrapper<SystemPreset>()
                .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
                .eq(SystemPreset::getCapability, normalizedCapability)
                .eq(SystemPreset::getIsDefault, true)
                .eq(SystemPreset::getIsActive, true)
                .last("LIMIT 1"));
        if (systemPreset != null) {
            return fromSystem(systemPreset);
        }

        throw new BusinessException(ErrorCode.NO_DEFAULT_CONFIG);
    }

    private String normalizeCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY, "能力标识不能为空");
        }
        llmCapabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }

    private EffectiveLLMConfigDTO fromUser(UserLLMConfig config) {
        EffectiveLLMConfigDTO dto = new EffectiveLLMConfigDTO();
        dto.setSource(SOURCE_USER);
        dto.setConfigId(config.getId());
        dto.setProviderId(config.getProviderId());
        dto.setProviderType(config.getProviderType());
        dto.setModelName(config.getModelName());
        dto.setCapability(config.getCapability());
        dto.setProtocol(config.getProtocol());
        dto.setApiBaseUrl(config.getApiBaseUrl());
        dto.setApiKeyMasked(apiKeyEncryptService.maskApiKey(config.getApiKey()));
        return dto;
    }

    private EffectiveLLMConfigDTO fromSystem(SystemPreset preset) {
        EffectiveLLMConfigDTO dto = new EffectiveLLMConfigDTO();
        dto.setSource(SOURCE_SYSTEM);
        dto.setConfigId(preset.getId());
        dto.setProviderId(preset.getProviderId());
        dto.setProviderType(preset.getProviderType());
        dto.setModelName(preset.getModelName());
        dto.setCapability(preset.getCapability());
        dto.setProtocol(preset.getProtocol());
        dto.setApiBaseUrl(preset.getApiBaseUrl());
        dto.setApiKeyMasked(apiKeyEncryptService.maskApiKey(preset.getApiKey()));
        return dto;
    }
}
