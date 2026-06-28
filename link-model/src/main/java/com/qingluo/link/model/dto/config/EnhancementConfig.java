package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Markdown 增强配置（3 个开关），字段名与 Python {@code EnhancementConfig} 对齐。
 *
 * <p>增强模型不在数据集层配置：开启增强时由 Python 取发起用户的默认 CHAT/VISION 模型
 * （LINK-148 PR #190）。{@code ignoreUnknown} 容忍历史 JSON 残留的 {@code table_model}/
 * {@code vision_model} 键，落库时自动丢弃，无需数据迁移。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Markdown 增强配置")
public class EnhancementConfig {

    @Schema(description = "表格 AI 增强开关", example = "true")
    private Boolean enableTableEnhancement;

    @Schema(description = "图片 AI 增强开关", example = "true")
    private Boolean enableImageEnhancement;

    @Schema(description = "标题层级增强开关", example = "true")
    private Boolean enableHeadingHierarchy;
}
