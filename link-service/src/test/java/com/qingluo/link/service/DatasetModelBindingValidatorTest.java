package com.qingluo.link.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetModelBindingValidatorTest {

    @Mock
    private LLMModelConfigValidator configValidator;

    private DatasetModelBindingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DatasetModelBindingValidator(configValidator);
    }

    @Test
    void createRequiresDenseAndSparseBindings() {
        assertInvalidField(() -> validator.validateForCreate(1L, null, 2L),
            "dense_embedding_config_id");
        verify(configValidator, never()).requireExecutable(anyLong(), anyLong(), eq("EMBEDDING"));
    }

    @Test
    void denseAndSparseBindingsAreImmutableAfterFirstSave() {
        DatasetParseConfig existing = new DatasetParseConfig();
        existing.setDenseEmbeddingConfigId(10L);
        existing.setSparseEmbeddingConfigId(11L);
        UpdateDatasetParseConfigRequest request = baseRequest();
        request.setDenseEmbeddingConfigId(12L);

        assertInvalidField(() -> validator.validateForUpdate(1L, existing, request),
            "dense_embedding_config_id");
    }

    @Test
    void enabledFeaturesRequireTheirExactCapabilityBindings() {
        UpdateDatasetParseConfigRequest request = baseRequest();
        EnhancementConfig enhancement = new EnhancementConfig();
        enhancement.setEnableHeadingHierarchy(true);
        request.setEnhancement(enhancement);

        assertInvalidField(() -> validator.validateForUpdate(1L, null, request),
            "enhancement_chat_config_id");

        enhancement.setEnableHeadingHierarchy(false);
        enhancement.setEnableImageEnhancement(true);
        assertInvalidField(() -> validator.validateForUpdate(1L, null, request),
            "enhancement_vision_config_id");

        enhancement.setEnableImageEnhancement(false);
        RecallConfig recall = new RecallConfig();
        recall.setEnableRerank(true);
        request.setRecall(recall);
        assertInvalidField(() -> validator.validateForUpdate(1L, null, request),
            "rerank_config_id");
    }

    @Test
    void storedRecallReportsAllMissingRequiredBindingsBeforeExactValidation() {
        DatasetParseConfig config = new DatasetParseConfig();
        RecallConfig recall = new RecallConfig();
        recall.setEnableRerank(true);
        config.setRecallConfig(recall);

        assertThatThrownBy(() -> validator.validateStoredRequiredBindings(
            1L, config, DatasetModelBindingValidator.Purpose.RECALL))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(ErrorCode.DATASET_MODEL_BINDING_REQUIRED.getCode());
                assertThat(exception.getDetails().get("missing_bindings"))
                    .isEqualTo(java.util.List.of(
                        "dense_embedding_config_id", "sparse_embedding_config_id", "rerank_config_id"));
            });
        verify(configValidator, never()).requireExecutable(anyLong(), anyLong(), eq("EMBEDDING"));
    }

    private UpdateDatasetParseConfigRequest baseRequest() {
        UpdateDatasetParseConfigRequest request = new UpdateDatasetParseConfigRequest();
        request.setDenseEmbeddingConfigId(10L);
        request.setSparseEmbeddingConfigId(11L);
        return request;
    }

    private void assertInvalidField(Runnable action, String field) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_DATASET_MODEL_BINDING.getCode());
                assertThat(exception.getDetails()).containsEntry("field", field);
            });
    }
}
