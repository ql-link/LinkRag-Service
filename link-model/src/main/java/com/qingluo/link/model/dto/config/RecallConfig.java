package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import lombok.Data;

/**
 * 召回检索配置（14 项），字段名与 Python {@code RecallConfig} 对齐。
 *
 * <p>正整数、非负分数/权重、枚举值与 Python Pydantic validator 对齐。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "召回检索配置")
public class RecallConfig {

    @Min(value = 1, message = "recall_result_limit 必须为正整数")
    @Schema(description = "召回结果上限", example = "20")
    private Integer recallResultLimit;

    @Schema(description = "生成上下文 token 预算", example = "4000")
    private Integer recallContextTokenBudget;

    @Min(value = 1, message = "bm25_top_k 必须为正整数")
    @Schema(description = "BM25 召回 TopK", example = "20")
    private Integer bm25TopK;

    @Min(value = 1, message = "sparse_top_k 必须为正整数")
    @Schema(description = "稀疏召回 TopK", example = "10")
    private Integer sparseTopK;

    @DecimalMin(value = "0.0", message = "sparse_score_threshold 必须不小于 0")
    @Schema(description = "稀疏召回分数阈值", example = "0.0")
    private Double sparseScoreThreshold;

    @Min(value = 1, message = "dense_top_k 必须为正整数")
    @Schema(description = "稠密召回 TopK", example = "10")
    private Integer denseTopK;

    @DecimalMin(value = "0.0", message = "dense_score_threshold 必须不小于 0")
    @Schema(description = "稠密召回分数阈值", example = "0.0")
    private Double denseScoreThreshold;

    @Schema(description = "启用的召回来源", example = "[\"bm25\",\"sparse\",\"dense\"]",
        allowableValues = {"bm25", "sparse", "dense"})
    private List<String> recallEnabledSources;

    @Schema(description = "多路召回融合策略", example = "rrf", allowableValues = {"rrf", "weighted_score"})
    private String recallFusionStrategy;

    @DecimalMin(value = "0.0", message = "fusion_bm25_weight 必须不小于 0")
    @Schema(description = "BM25 融合权重", example = "1.0")
    private Double fusionBm25Weight;

    @DecimalMin(value = "0.0", message = "fusion_sparse_weight 必须不小于 0")
    @Schema(description = "稀疏召回融合权重", example = "1.0")
    private Double fusionSparseWeight;

    @DecimalMin(value = "0.0", message = "fusion_dense_weight 必须不小于 0")
    @Schema(description = "稠密召回融合权重", example = "1.0")
    private Double fusionDenseWeight;

    @Min(value = 1, message = "rerank_top_n 必须为正整数")
    @Schema(description = "重排后返回候选条数上限", example = "8")
    private Integer rerankTopN;

    @Schema(description = "召回严格模式：true 为任一路失败即整体失败，false 为允许单路失败降级", example = "false")
    private Boolean recallStrict;
}
