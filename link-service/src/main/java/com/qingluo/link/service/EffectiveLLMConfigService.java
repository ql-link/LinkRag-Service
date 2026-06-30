package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.EffectiveLLMConfigDTO;

/**
 * 解析当前用户某能力实际生效的 LLM 配置。
 */
public interface EffectiveLLMConfigService {

    /**
     * 解析顺序：用户自配默认 -> LinkRag 系统兜底默认。
     */
    EffectiveLLMConfigDTO getEffectiveConfig(Long userId, String capability);
}
