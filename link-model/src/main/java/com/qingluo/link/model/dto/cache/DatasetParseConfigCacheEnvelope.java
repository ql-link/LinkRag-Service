package com.qingluo.link.model.dto.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据集解析配置的版本化 FOUND / NOT_FOUND 缓存信封。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatasetParseConfigCacheEnvelope {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String FOUND = "FOUND";
    public static final String NOT_FOUND = "NOT_FOUND";

    private int schemaVersion;
    private String state;
    private DatasetParseConfigSnapshot value;

    public static DatasetParseConfigCacheEnvelope found(DatasetParseConfigSnapshot value) {
        return new DatasetParseConfigCacheEnvelope(CURRENT_SCHEMA_VERSION, FOUND, value);
    }

    public static DatasetParseConfigCacheEnvelope notFound() {
        return new DatasetParseConfigCacheEnvelope(CURRENT_SCHEMA_VERSION, NOT_FOUND, null);
    }

    @JsonIgnore
    public boolean isFound() {
        return FOUND.equals(state);
    }

    public boolean isValidFor(Long userId, Long datasetId) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return false;
        }
        if (NOT_FOUND.equals(state)) {
            return value == null;
        }
        return FOUND.equals(state)
            && value != null
            && Objects.equals(value.getUserId(), userId)
            && Objects.equals(value.getDatasetId(), datasetId)
            && value.getChunkingConfig() != null
            && value.getEnhancementConfig() != null
            && value.getPdfConfig() != null
            && value.getRecallConfig() != null
            && value.getIsActive() != null;
    }
}
