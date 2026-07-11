package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前周期与上一等长周期指标")
public class AdminUserPeriodMetricDTO {
    private long current;
    private long previous;
    @Schema(description = "环比增长率；上一周期为0时为null")
    private Double growthRate;
}
