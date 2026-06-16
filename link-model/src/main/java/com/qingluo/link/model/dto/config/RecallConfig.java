package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 召回检索配置（6 项），字段名与 Python {@code RecallConfig} 对齐。
 *
 * <p>不加范围校验：Python 对召回项无 validator，数值范围交 Python 与前端 UI 把关（brief 决策 6）。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "召回检索配置")
public class RecallConfig {

    @Schema(description = "召回结果上限", example = "20")
    private Integer recallResultLimit;

    @Schema(description = "生成上下文 token 预算", example = "4000")
    private Integer recallContextTokenBudget;

    @Schema(description = "稀疏召回 TopK", example = "10")
    private Integer sparseTopK;

    @Schema(description = "稀疏召回分数阈值", example = "0.0")
    private Double sparseScoreThreshold;

    @Schema(description = "稠密召回 TopK", example = "10")
    private Integer denseTopK;

    @Schema(description = "稠密召回分数阈值", example = "0.0")
    private Double denseScoreThreshold;
}
