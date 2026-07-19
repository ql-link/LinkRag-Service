package com.qingluo.link.service;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据集五类模型绑定的唯一 Java 写入校验入口。
 */
@Component
@RequiredArgsConstructor
public class DatasetModelBindingValidator {

    public enum Purpose {
        PARSE,
        RECALL
    }

    private final LLMModelConfigValidator configValidator;

    public ValidatedBindings validateForCreate(Long userId, Long denseConfigId, Long sparseConfigId) {
        requireBinding(userId, denseConfigId, "EMBEDDING", "dense_embedding_config_id");
        requireBinding(userId, sparseConfigId, "SPARSE_EMBEDDING", "sparse_embedding_config_id");
        return new ValidatedBindings(denseConfigId, sparseConfigId, null, null, null);
    }

    public ValidatedBindings validateForUpdate(Long userId, DatasetParseConfig existing,
                                                UpdateDatasetParseConfigRequest request) {
        assertImmutable(existing == null ? null : existing.getDenseEmbeddingConfigId(),
            request.getDenseEmbeddingConfigId(), "dense_embedding_config_id");
        assertImmutable(existing == null ? null : existing.getSparseEmbeddingConfigId(),
            request.getSparseEmbeddingConfigId(), "sparse_embedding_config_id");

        requireBinding(userId, request.getDenseEmbeddingConfigId(),
            "EMBEDDING", "dense_embedding_config_id");
        requireBinding(userId, request.getSparseEmbeddingConfigId(),
            "SPARSE_EMBEDDING", "sparse_embedding_config_id");

        EnhancementConfig enhancement = request.getEnhancement();
        if (isEnabled(enhancement == null ? null : enhancement.getEnableTableEnhancement())
            || isEnabled(enhancement == null ? null : enhancement.getEnableHeadingHierarchy())) {
            requireBinding(userId, request.getEnhancementChatConfigId(),
                "CHAT", "enhancement_chat_config_id");
        } else if (request.getEnhancementChatConfigId() != null) {
            requireBinding(userId, request.getEnhancementChatConfigId(),
                "CHAT", "enhancement_chat_config_id");
        }
        if (isEnabled(enhancement == null ? null : enhancement.getEnableImageEnhancement())) {
            requireBinding(userId, request.getEnhancementVisionConfigId(),
                "VISION", "enhancement_vision_config_id");
        } else if (request.getEnhancementVisionConfigId() != null) {
            requireBinding(userId, request.getEnhancementVisionConfigId(),
                "VISION", "enhancement_vision_config_id");
        }

        RecallConfig recall = request.getRecall();
        if (isEnabled(recall == null ? null : recall.getEnableRerank())) {
            requireBinding(userId, request.getRerankConfigId(), "RERANK", "rerank_config_id");
        } else if (request.getRerankConfigId() != null) {
            requireBinding(userId, request.getRerankConfigId(), "RERANK", "rerank_config_id");
        }
        return new ValidatedBindings(
            request.getDenseEmbeddingConfigId(), request.getSparseEmbeddingConfigId(),
            request.getEnhancementChatConfigId(), request.getEnhancementVisionConfigId(),
            request.getRerankConfigId());
    }

    public void validateStoredRequiredBindings(Long userId, DatasetParseConfig config, Purpose purpose) {
        List<String> missing = new ArrayList<>();
        if (config == null || config.getDenseEmbeddingConfigId() == null) {
            missing.add("dense_embedding_config_id");
        }
        if (config == null || config.getSparseEmbeddingConfigId() == null) {
            missing.add("sparse_embedding_config_id");
        }
        if (config != null && purpose == Purpose.PARSE) {
            EnhancementConfig enhancement = config.getEnhancementConfig();
            if ((isEnabled(enhancement == null ? null : enhancement.getEnableTableEnhancement())
                || isEnabled(enhancement == null ? null : enhancement.getEnableHeadingHierarchy()))
                && config.getEnhancementChatConfigId() == null) {
                missing.add("enhancement_chat_config_id");
            }
            if (isEnabled(enhancement == null ? null : enhancement.getEnableImageEnhancement())
                && config.getEnhancementVisionConfigId() == null) {
                missing.add("enhancement_vision_config_id");
            }
        }
        if (config != null && purpose == Purpose.RECALL
            && isEnabled(config.getRecallConfig() == null ? null : config.getRecallConfig().getEnableRerank())
            && config.getRerankConfigId() == null) {
            missing.add("rerank_config_id");
        }
        if (!missing.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("missing_bindings", missing);
            throw new BusinessException(ErrorCode.DATASET_MODEL_BINDING_REQUIRED, details);
        }

        configValidator.requireExecutable(userId, config.getDenseEmbeddingConfigId(), "EMBEDDING");
        configValidator.requireExecutable(userId, config.getSparseEmbeddingConfigId(), "SPARSE_EMBEDDING");
        if (purpose == Purpose.PARSE) {
            validateStoredParseEnhancements(userId, config);
        } else if (isEnabled(config.getRecallConfig() == null
            ? null : config.getRecallConfig().getEnableRerank())) {
            configValidator.requireExecutable(userId, config.getRerankConfigId(), "RERANK");
        }
    }

    private void validateStoredParseEnhancements(Long userId, DatasetParseConfig config) {
        EnhancementConfig enhancement = config.getEnhancementConfig();
        if (isEnabled(enhancement == null ? null : enhancement.getEnableTableEnhancement())
            || isEnabled(enhancement == null ? null : enhancement.getEnableHeadingHierarchy())) {
            configValidator.requireExecutable(userId, config.getEnhancementChatConfigId(), "CHAT");
        }
        if (isEnabled(enhancement == null ? null : enhancement.getEnableImageEnhancement())) {
            configValidator.requireExecutable(userId, config.getEnhancementVisionConfigId(), "VISION");
        }
    }

    private void requireBinding(Long userId, Long configId, String capability, String field) {
        if (configId == null) {
            throw invalidBinding(field);
        }
        try {
            configValidator.requireExecutable(userId, configId, capability);
        } catch (BusinessException ex) {
            throw invalidBinding(field);
        }
    }

    private void assertImmutable(Long existingId, Long requestedId, String field) {
        if (existingId != null && !Objects.equals(existingId, requestedId)) {
            throw invalidBinding(field);
        }
    }

    private BusinessException invalidBinding(String field) {
        return new BusinessException(ErrorCode.INVALID_DATASET_MODEL_BINDING, Map.of("field", field));
    }

    private boolean isEnabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    public record ValidatedBindings(Long denseConfigId, Long sparseConfigId,
                                    Long enhancementChatConfigId, Long enhancementVisionConfigId,
                                    Long rerankConfigId) {
    }
}
