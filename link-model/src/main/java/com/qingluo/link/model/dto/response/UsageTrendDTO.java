package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量环比趋势 DTO（当前周期 vs 等长上一周期）。
 *
 * <p>增长率为包装类型 {@link Double}：上一周期为 0（无可比基数）时为 {@code null}，前端据此显示「—」而非 +∞。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用量环比趋势")
public class UsageTrendDTO {

    @Schema(description = "当前周期总Token数", example = "5500")
    private long currentTokens;

    @Schema(description = "上一周期总Token数", example = "4661")
    private long previousTokens;

    @Schema(description = "当前周期调用次数", example = "17")
    private long currentCalls;

    @Schema(description = "上一周期调用次数", example = "14")
    private long previousCalls;

    @Schema(description = "Token 环比增长率（0.18=+18%）；上一周期为 0 时为 null", example = "0.18")
    private Double tokenGrowthRate;

    @Schema(description = "调用次数环比增长率；上一周期为 0 时为 null", example = "0.2143")
    private Double callGrowthRate;
}
