package com.qingluo.link.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLLMConfigDTO {
    private String id;
    private String configName;
    private String providerType;
    private String providerName;
    private String modelName;
    private String capabilities;
    private String apiKeyMasked;
    private String customApiBaseUrl;
    private Integer priority;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer timeoutMs;
    private Integer maxRetries;
    private Boolean streamEnabled;
    private String extraConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}