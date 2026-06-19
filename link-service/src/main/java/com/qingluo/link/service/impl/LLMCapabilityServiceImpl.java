package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * LLM 模型能力校验服务实现。
 *
 * <p>仅维护受支持能力词汇表并校验入参；模型与能力的对应关系由 llm_provider_model 表表达。</p>
 */
@Service
public class LLMCapabilityServiceImpl implements LLMCapabilityService {

    private static final Set<String> SUPPORTED_CAPABILITIES = Set.of(
            "CHAT", "EMBEDDING", "SPARSE_EMBEDDING", "VISION", "RERANK", "ASR"
    );

    @Override
    public void validateCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY);
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CAPABILITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY);
        }
    }
}
