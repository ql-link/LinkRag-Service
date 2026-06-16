package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

/**
 * 分块策略配置（3 项），字段名与 Python {@code ChunkingConfig} Pydantic 模型对齐。
 *
 * <p>包装类型 + {@code NON_NULL}：用户未提交的字段不写入 JSON，由 Python 消费时按模型补默认
 * （Java 不持有默认值）。{@code ignoreUnknown}：容忍 Python 后续新增字段，避免反序列化失败。
 * 仅对会直接导致解析失败的项做范围校验（与 Python validator 一致），其余范围交 Python。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "分块策略配置")
public class ChunkingConfig {

    @Schema(description = "标题分块层级", example = "5")
    private Integer headingBreakLevel;

    @Min(value = 128, message = "min_candidate_chunk_tokens 必须在 128-256 之间")
    @Max(value = 256, message = "min_candidate_chunk_tokens 必须在 128-256 之间")
    @Schema(description = "候选块最小 token", example = "128")
    private Integer minCandidateChunkTokens;

    @Min(value = 0, message = "overlap_tokens 必须在 0-64 之间")
    @Max(value = 64, message = "overlap_tokens 必须在 0-64 之间")
    @Schema(description = "块间重叠 token", example = "64")
    private Integer overlapTokens;
}
