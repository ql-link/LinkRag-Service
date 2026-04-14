package com.qingluo.link.model.dto.request;

import lombok.Data;

/**
 * 更新系统厂商请求
 */
@Data
public class UpdateProviderRequest {

    private String providerName;
    private String apiBaseUrl;
    private String supportedModels;
    private String configSchema;
    private Boolean isActive;
    private Integer priority;
}
