package com.qingluo.link.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新系统厂商请求
 */
@Data
@Schema(description = "更新系统厂商请求")
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateProviderRequest {

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "API地址", example = "https://api.openai.com/v1")
    private String apiBaseUrl;

    @Schema(description = "支持的能力列表(JSON数组)", example = "[\"CHAT\",\"EMBEDDING\"]")
    private String supportedCapabilities;

    @Deprecated
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "已废弃：不再接收系统模型列表", hidden = true)
    private String supportedModels;

    @Schema(description = "配置Schema(JSON格式)")
    private String configSchema;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "优先级", example = "10")
    private Integer priority;
}
