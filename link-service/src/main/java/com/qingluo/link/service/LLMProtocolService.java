package com.qingluo.link.service;

/**
 * LLM 调用协议校验服务。
 *
 * <p>协议收敛为 openai/anthropic/google/jina/dashscope 5 个 API 家族；本服务统一校验
 * 协议取值是否在受支持集合内，避免非规范写法落库导致下游 adapter 选择失败。</p>
 */
public interface LLMProtocolService {

    /**
     * 校验协议是否受支持，非法时抛出 INVALID_PROTOCOL。
     */
    void validateProtocol(String protocol);
}
