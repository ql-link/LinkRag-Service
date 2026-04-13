package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLLMConfigDTO {

    private Long id;
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