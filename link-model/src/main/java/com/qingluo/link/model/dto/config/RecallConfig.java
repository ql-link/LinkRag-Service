package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.Min;
import lombok.Data;

/**
 * 召回检索配置（9 项），字段名与 Python {@code RecallConfig} 对齐。
 *
 * <p>历史 6 项不加范围校验：Python 对这些召回项无 validator，数值范围交 Python 与前端 UI 把关。
 * LINK-170 新增的 {@code rerank_top_n} 与 Python Pydantic validator 对齐，必须为正整数。
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

    @Schema(description = "启用的召回来源", example = "[\"bm25\",\"sparse\",\"dense\"]",
        allowableValues = {"bm25", "sparse", "dense"})
    private List<String> recallEnabledSources;

    @Min(value = 1, message = "rerank_top_n 必须为正整数")
    @Schema(description = "重排后返回候选条数上限", example = "8")
    private Integer rerankTopN;

    @Schema(description = "召回严格模式：true 为任一路失败即整体失败，false 为允许单路失败降级", example = "false")
    private Boolean recallStrict;
}
