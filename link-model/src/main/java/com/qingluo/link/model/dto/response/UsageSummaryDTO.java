package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量汇总 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryDTO {

    private long totalCalls;
    private long totalTokens;
    private long promptTokens;
    private long completionTokens;
    private double averageLatencyMs;
}