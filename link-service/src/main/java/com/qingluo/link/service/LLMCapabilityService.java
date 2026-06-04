package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemProvider;

import java.util.List;

/**
 * LLM 能力解析服务。
 *
 * <p>负责把系统厂商表中的 supported_capabilities JSON 转换为稳定的能力列表，
 * 并统一校验能力值和厂商能力匹配关系。</p>
 */
public interface LLMCapabilityService {

    List<String> parseSupportedCapabilities(String supportedCapabilities);

    void ensureProviderSupports(SystemProvider provider, String capability);

    void validateCapability(String capability);
}
