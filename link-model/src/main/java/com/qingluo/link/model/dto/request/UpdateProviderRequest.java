package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新系统厂商请求
 */
@Data
@Schema(description = "更新系统厂商请求")
public class UpdateProviderRequest {

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "API地址", example = "https://api.openai.com/v1")
    private String apiBaseUrl;

    @Schema(description = "支持的模型列表(JSON数组)", example = "[\"gpt-4\",\"gpt-3.5-turbo\"]")
    private String supportedModels;

    @Schema(description = "配置Schema(JSON格式)")
    private String configSchema;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "优先级", example = "10")
    private Integer priority;
}
