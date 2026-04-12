package com.qingluo.link.core.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigRequest {
    private String apiKey;
    private String customApiBaseUrl;
    private Integer priority;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer timeoutMs;
    private Integer maxRetries;
    private Boolean streamEnabled;
    private String extraConfig;
}