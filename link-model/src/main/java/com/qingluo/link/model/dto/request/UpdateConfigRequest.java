package com.qingluo.link.model.dto.request;

import lombok.Data;

/**
 * 更新 LLM 配置请求
 */
@Data
public class UpdateConfigRequest {

    private String apiKey;
    private Integer priority;
    private Boolean isActive;
    private Boolean isDefault;
    private Integer timeoutMs;
    private Integer maxRetries;
    private Boolean streamEnabled;
    private String extraConfig;
}