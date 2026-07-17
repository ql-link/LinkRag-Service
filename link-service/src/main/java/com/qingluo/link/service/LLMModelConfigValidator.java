package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.LLMModelConfig;

/**
 * 精确配置校验入口；不解析默认关系。
 */
public interface LLMModelConfigValidator {

    LLMModelConfig requireExecutable(Long actorUserId, Long configId, String requiredCapability);
}
