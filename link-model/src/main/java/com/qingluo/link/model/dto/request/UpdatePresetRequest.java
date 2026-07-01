package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新系统预设请求（管理端）。
 */
@Data
@Schema(description = "更新系统预设请求")
public class UpdatePresetRequest {

    @Schema(description = "源模型能力目录项 ID；传入时从 llm_provider_model 快捷复制模型名、展示名、能力、协议和入口", example = "10001")
    private Long sourceProviderModelId;

    @Schema(description = "兼容字段：源厂商ID；配合 modelName/capability 从正式模型目录快捷复制", example = "1")
    private Long providerId;

    @Schema(description = "模型名称", example = "deepseek-v3")
    private String modelName;

    @Schema(description = "模型展示名；为空字符串可清空", example = "DeepSeek V3")
    private String displayName;

    @Schema(description = "能力标识，如 CHAT/EMBEDDING/SPARSE_EMBEDDING", example = "CHAT")
    private String capability;

    @Schema(description = "调用协议，合法值 openai/anthropic/google/jina/dashscope", example = "openai")
    private String protocol;

    @Schema(description = "调用入口完整端点 URL", example = "https://api.deepseek.com/v1/chat/completions")
    private String apiBaseUrl;

    @Schema(description = "平台 Key（明文，入库前加密）", example = "sk-platform-xxxxx")
    private String apiKey;

    @Schema(description = "是否启用为系统兜底候选", example = "true")
    private Boolean isActive;

    @Schema(description = "是否设为该能力的系统兜底默认配置", example = "false")
    private Boolean isDefault;
}
