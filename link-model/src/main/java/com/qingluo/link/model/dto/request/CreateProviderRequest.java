package com.qingluo.link.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建系统厂商请求
 */
@Data
@Schema(description = "创建系统厂商请求")
@JsonIgnoreProperties(ignoreUnknown = false)
public class CreateProviderRequest {

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "厂商名称不能为空")
    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @NotBlank(message = "API 地址不能为空")
    @Schema(description = "API地址", example = "https://api.openai.com/v1")
    private String apiBaseUrl;

    @NotBlank(message = "支持能力不能为空")
    @Schema(description = "支持的能力列表(JSON数组)", example = "[\"CHAT\",\"EMBEDDING\"]")
    private String supportedCapabilities;

    @Deprecated
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "已废弃：不再接收系统模型列表", hidden = true)
    private String supportedModels;

    @Schema(description = "配置Schema(JSON格式)")
    private String configSchema;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @NotNull(message = "优先级不能为空")
    @Schema(description = "优先级", example = "10")
    private Integer priority;
}
