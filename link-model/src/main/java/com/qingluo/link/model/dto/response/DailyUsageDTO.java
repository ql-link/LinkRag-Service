package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日度用量 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUsageDTO {

    private String date;
    private long calls;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}