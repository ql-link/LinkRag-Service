package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 创建 LLM 配置请求
 */
@Data
@Schema(description = "创建LLM配置请求")
public class CreateConfigRequest {

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "配置名称不能为空")
    @Schema(description = "配置名称", example = "我的OpenAI配置")
    private String configName;

    @NotBlank(message = "API Key 不能为空")
    @Schema(description = "API Key", example = "sk-xxxxx")
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "模型能力，可选，如CHAT/OCR/EMBEDDING；不传则按模型支持的全部能力展开", example = "CHAT")
    private String capability;

    @Schema(description = "自定义API地址，可选", example = "https://api.openai.com/v1")
    private String customApiBaseUrl;

    @Schema(description = "优先级", example = "50")
    private Integer priority = 50;

    @Schema(description = "是否为默认配置", example = "false")
    private Boolean isDefault = false;

    @Schema(description = "超时时间(毫秒)", example = "60000")
    private Integer timeoutMs = 60000;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetries = 3;

    @Schema(description = "是否启用流式响应", example = "true")
    private Boolean streamEnabled = true;

    @Schema(description = "额外配置(JSON格式)")
    private String extraConfig;
}