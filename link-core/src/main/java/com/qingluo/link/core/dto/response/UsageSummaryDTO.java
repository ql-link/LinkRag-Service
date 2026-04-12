package com.qingluo.link.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryDTO {
    private long totalCalls;
    private long totalTokens;
    private long promptTokens;
    private long completionTokens;
    private Double averageLatencyMs;
}