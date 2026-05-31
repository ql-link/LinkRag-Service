package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemProvider;

import java.util.List;
import java.util.Map;

/**
 * LLM 模型能力解析服务。
 *
 * <p>负责把系统厂商表中的 supported_models JSON 转换为稳定的模型能力目录，
 * 并统一校验能力值和模型能力匹配关系。</p>
 */
public interface LLMCapabilityService {

    Map<String, List<String>> parseSupportedModels(String supportedModels);

    List<String> getModelCapabilities(SystemProvider provider, String modelName);

    void validateCapability(String capability);
}
