package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新 LLM 配置请求
 */
@Data
@Schema(description = "更新LLM配置请求")
public class UpdateConfigRequest {

    @Schema(description = "API Key", example = "sk-xxxxx")
    private String apiKey;

    @Schema(description = "优先级", example = "50")
    private Integer priority;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "是否为默认配置", example = "false")
    private Boolean isDefault;

    @Schema(description = "超时时间(毫秒)", example = "60000")
    private Integer timeoutMs;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetries;

    @Schema(description = "是否启用流式响应", example = "true")
    private Boolean streamEnabled;

    @Schema(description = "自定义API地址，可选", example = "https://api.openai.com/v1")
    private String customApiBaseUrl;

    @Schema(description = "额外配置(JSON格式)")
    private String extraConfig;
}