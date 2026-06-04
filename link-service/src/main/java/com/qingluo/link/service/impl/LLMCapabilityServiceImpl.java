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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LLM 模型能力解析服务实现。
 *
 * <p>新数据使用 {"model":["CHAT"]} 结构；为了兼容旧测试和旧数据，若读取到
 * ["gpt-4"] 这类数组结构，则按 CHAT 能力处理。</p>
 */
@Service
@RequiredArgsConstructor
public class LLMCapabilityServiceImpl implements LLMCapabilityService {

    private static final String DEFAULT_LEGACY_CAPABILITY = "CHAT";
    private static final Set<String> SUPPORTED_CAPABILITIES = Set.of(
            "CHAT", "EMBEDDING", "OCR", "VISION", "REASONING", "CODE",
            "TOOL_CALLING", "RERANK"
    );

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, List<String>> parseSupportedModels(String supportedModels) {
        if (!StringUtils.hasText(supportedModels)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(supportedModels);
            if (root.isTextual()) {
                return parseSupportedModels(root.asText());
            }
            if (root.isObject()) {
                Map<String, List<String>> raw = objectMapper.convertValue(
                        root, new TypeReference<Map<String, List<String>>>() {
                        });
                Map<String, List<String>> normalized = new LinkedHashMap<>();
                raw.forEach((model, capabilities) ->
                        normalized.put(model, normalizeCapabilities(capabilities)));
                return normalized;
            }
            if (root.isArray()) {
                Map<String, List<String>> legacyModels = new LinkedHashMap<>();
                for (JsonNode node : root) {
                    if (node.isTextual() && StringUtils.hasText(node.asText())) {
                        legacyModels.put(node.asText(), List.of(DEFAULT_LEGACY_CAPABILITY));
                    }
                }
                return legacyModels;
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "厂商模型能力配置不合法");
        }
        throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "厂商模型能力配置不合法");
    }

    @Override
    public List<String> getModelCapabilities(SystemProvider provider, String modelName) {
        Map<String, List<String>> supportedModels = parseSupportedModels(provider.getSupportedModels());
        List<String> capabilities = supportedModels.get(modelName);
        if (capabilities == null || capabilities.isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED);
        }
        return capabilities;
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
        return new ArrayList<>(normalized);
    }
}
