package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建系统厂商请求
 */
@Data
@Schema(description = "创建系统厂商请求")
public class CreateProviderRequest {

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "厂商名称不能为空")
    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @NotBlank(message = "API 地址不能为空")
    @Schema(description = "默认API地址（模板值）", example = "https://api.openai.com/v1")
    private String apiBaseUrl;

    @NotBlank(message = "默认协议不能为空")
    @Schema(description = "默认协议（模板值，用于新增模型能力预填）", example = "openai")
    private String defaultProtocol;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @NotNull(message = "优先级不能为空")
    @Schema(description = "优先级", example = "10")
    private Integer priority;
}
