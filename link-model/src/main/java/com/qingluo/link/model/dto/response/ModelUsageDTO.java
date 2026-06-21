package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按「厂商 + 模型」聚合的用量 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按模型聚合用量")
public class ModelUsageDTO {

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "调用次数", example = "12")
    private long calls;

    @Schema(description = "提示词Token数", example = "3400")
    private long promptTokens;

    @Schema(description = "补全Token数", example = "2100")
    private long completionTokens;

    @Schema(description = "总Token数", example = "5500")
    private long totalTokens;
}
