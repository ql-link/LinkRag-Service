package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日度用量 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日度用量")
public class DailyUsageDTO {

    @Schema(description = "日期", example = "2024-01-01")
    private String date;

    @Schema(description = "调用次数", example = "100")
    private long calls;

    @Schema(description = "提示词Token数", example = "5000")
    private long promptTokens;

    @Schema(description = "补全Token数", example = "3000")
    private long completionTokens;

    @Schema(description = "总Token数", example = "8000")
    private long totalTokens;
}