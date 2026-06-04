package com.qingluo.link.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * LLM 能力解析服务实现。
 */
@Service
@RequiredArgsConstructor
public class LLMCapabilityServiceImpl implements LLMCapabilityService {

    private static final Set<String> SUPPORTED_CAPABILITIES = Set.of(
            "CHAT", "EMBEDDING", "OCR", "VISION", "REASONING", "CODE",
            "TOOL_CALLING", "RERANK"
    );

    private final ObjectMapper objectMapper;

    @Override
    public List<String> parseSupportedCapabilities(String supportedCapabilities) {
        if (!StringUtils.hasText(supportedCapabilities)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(supportedCapabilities);
            if (root.isTextual()) {
                return parseSupportedCapabilities(root.asText());
            }
            if (root.isArray()) {
                List<String> raw = objectMapper.convertValue(root, new TypeReference<List<String>>() {
                });
                return normalizeCapabilities(raw);
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "厂商能力配置不合法");
        }
        throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "厂商能力配置必须是 JSON 数组");
    }

    @Override
    public void ensureProviderSupports(SystemProvider provider, String capability) {
        validateCapability(capability);
        String normalized = capability.toUpperCase(Locale.ROOT);
        List<String> capabilities = parseSupportedCapabilities(provider.getSupportedCapabilities());
        if (!capabilities.contains(normalized)) {
            throw new BusinessException(ErrorCode.PROVIDER_CAPABILITY_UNSUPPORTED);
        }
    }

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

    private List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            if (!StringUtils.hasText(capability)) {
                continue;
            }
            String value = capability.toUpperCase(Locale.ROOT);
            if (!SUPPORTED_CAPABILITIES.contains(value)) {
                throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
