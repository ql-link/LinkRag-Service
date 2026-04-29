package com.qingluo.link.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class KnowledgeFileConfigNormalizer {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private KnowledgeFileConfigNormalizer() {
    }

    public static LinkedHashSet<String> normalizeAndValidate(List<String> suffixes, Set<String> supportedSuffixes) {
        if (suffixes == null || suffixes.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }
        LinkedHashSet<String> normalized = suffixes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .peek(value -> {
                if (!supportedSuffixes.contains(value)) {
                    throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
                }
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }
        return normalized;
    }

    public static Set<String> parseSuffixes(String rawValue, Set<String> fallback, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(rawValue)) {
            return new LinkedHashSet<>(fallback);
        }
        try {
            List<String> values = objectMapper.readValue(rawValue, STRING_LIST_TYPE);
            LinkedHashSet<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            return normalized.isEmpty() ? new LinkedHashSet<>(fallback) : normalized;
        } catch (Exception e) {
            return new LinkedHashSet<>(fallback);
        }
    }

    public static Set<String> normalizeOrFallback(List<String> suffixes, Set<String> fallback) {
        if (suffixes == null || suffixes.isEmpty()) {
            return new LinkedHashSet<>(fallback);
        }
        LinkedHashSet<String> normalized = suffixes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!fallback.containsAll(normalized)) {
            return new LinkedHashSet<>(fallback);
        }
        return normalized.isEmpty() ? new LinkedHashSet<>(fallback) : normalized;
    }

    public static String writeSuffixes(Set<String> suffixes, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(suffixes);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }
    }
}
