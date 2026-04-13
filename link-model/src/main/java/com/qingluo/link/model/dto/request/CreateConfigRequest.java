package com.qingluo.link.model.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建 LLM 配置请求
 */
@Data
public class CreateConfigRequest {

    @NotBlank(message = "厂商类型不能为空")
    private String providerType;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private Integer priority = 50;

    private Boolean isDefault = false;

    private Integer timeoutMs = 60000;

    private Integer maxRetries = 3;

    private Boolean streamEnabled = true;

    private String extraConfig;
}