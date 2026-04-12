package com.qingluo.link.core.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {
    @NotBlank(message = "厂商类型不能为空")
    private String providerType;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private Integer priority;
    private Boolean isDefault;
    private Integer timeoutMs;
    private Integer maxRetries;
    private Boolean streamEnabled;
    private String extraConfig;
}