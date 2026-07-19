package com.qingluo.link.service.impl.llm;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.LLMModelConfigMapper;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Java 控制面的精确配置校验，与 Python 保持固定错误优先级。
 */
@Service
@RequiredArgsConstructor
public class LLMModelConfigValidatorImpl implements LLMModelConfigValidator {

    private final LLMModelConfigMapper configMapper;
    private final LLMCapabilityService capabilityService;

    @Override
    public LLMModelConfig requireExecutable(Long actorUserId, Long configId, String requiredCapability) {
        LLMModelConfig config = configId == null ? null : configMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_INACTIVE);
        }
        if (!LLMConfigScope.SYSTEM.name().equals(config.getScope())
            && !LLMConfigScope.USER.name().equals(config.getScope())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        if (LLMConfigScope.USER.name().equals(config.getScope())
            && !config.getOwnerUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        String normalizedCapability = normalizeCapability(requiredCapability);
        if (!normalizedCapability.equals(config.getCapability())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_CAPABILITY_MISMATCH);
        }
        return config;
    }

    private String normalizeCapability(String capability) {
        capabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }
}
