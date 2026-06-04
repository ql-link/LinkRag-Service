package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户LLM配置")
public class UserLLMConfigDTO {

    @Schema(description = "配置ID", example = "1")
    private Long id;

    @Schema(description = "配置名称", example = "我的OpenAI配置")
    private String configName;

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "专用能力标识：CHAT/EMBEDDING/RERANK/OCR", example = "CHAT")
    private String capability;

    @Schema(description = "API Key(脱敏)")
    private String apiKeyMasked;

    @Schema(description = "自定义API地址")
    private String customApiBaseUrl;

    @Schema(description = "优先级", example = "50")
    private Integer priority;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "是否为默认配置", example = "false")
    private Boolean isDefault;

    @Schema(description = "是否为系统预设配置", example = "false")
    private Boolean systemPreset;

    @Schema(description = "当前用户是否可编辑", example = "true")
    private Boolean editable;

    @Schema(description = "当前用户是否可选择使用", example = "true")
    private Boolean selectable;

    @Schema(description = "超时时间(毫秒)", example = "60000")
    private Integer timeoutMs;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetries;

    @Schema(description = "是否启用流式响应", example = "true")
    private Boolean streamEnabled;

    @Schema(description = "额外配置")
    private String extraConfig;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
