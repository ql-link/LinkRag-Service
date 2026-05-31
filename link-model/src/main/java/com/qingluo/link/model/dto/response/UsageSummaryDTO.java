package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量汇总 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用量汇总")
public class UsageSummaryDTO {

    @Schema(description = "总调用次数", example = "1000")
    private long totalCalls;

    @Schema(description = "总Token数", example = "50000")
    private long totalTokens;

    @Schema(description = "提示词Token数", example = "30000")
    private long promptTokens;

    @Schema(description = "补全Token数", example = "20000")
    private long completionTokens;

    @Schema(description = "平均延迟(毫秒)", example = "150.5")
    private double averageLatencyMs;
}