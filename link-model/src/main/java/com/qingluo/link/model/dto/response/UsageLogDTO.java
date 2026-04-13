package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用量明细 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageLogDTO {

    private Long id;
    private Long configId;
    private String providerType;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}