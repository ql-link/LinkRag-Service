package com.qingluo.link.service;

/**
 * LLM 模型能力校验服务。
 *
 * <p>模型能力目录已迁至 llm_provider_model 正表，本服务不再解析 supported_models JSON，
 * 仅统一校验能力词汇是否在受支持集合内。</p>
 */
public interface LLMCapabilityService {

    /**
     * 校验能力标识是否受支持，非法时抛出 INVALID_MODEL_CAPABILITY。
     */
    void validateCapability(String capability);
}
