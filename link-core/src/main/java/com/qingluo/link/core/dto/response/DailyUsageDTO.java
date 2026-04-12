package com.qingluo.link.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageDTO {
    private String date;
    private long calls;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}