package com.qingluo.link.model.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建系统厂商请求
 */
@Data
public class CreateProviderRequest {

    @NotBlank(message = "厂商类型不能为空")
    private String providerType;

    @NotBlank(message = "厂商名称不能为空")
    private String providerName;

    @NotBlank(message = "API 地址不能为空")
    private String apiBaseUrl;

    private String supportedModels;

    private String configSchema;

    @NotNull(message = "启用状态不能为空")
    private Boolean isActive;

    @NotNull(message = "优先级不能为空")
    private Integer priority;
}
