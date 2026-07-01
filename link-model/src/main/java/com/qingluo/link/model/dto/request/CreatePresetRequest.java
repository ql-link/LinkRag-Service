package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 创建系统预设请求（管理端）
 *
 * <p>预配一条 LinkRag 系统兜底配置。支持两种来源：
 * 1) sourceProviderModelId 快捷复制正式模型目录；
 * 2) 手动填写模型名、能力、协议和入口。
 * 用户无需配置 Key，平台 Key 由管理员维护并加密入库。</p>
 */
@Data
@Schema(description = "创建系统预设请求")
public class CreatePresetRequest {

    @Schema(description = "源模型能力目录项 ID；传入时从 llm_provider_model 快捷复制模型名、展示名、能力、协议和入口", example = "10001")
    private Long sourceProviderModelId;

    @Schema(description = "兼容字段：源厂商ID；未传 sourceProviderModelId 时可配合 modelName/capability 从正式模型目录快捷复制", example = "1")
    private Long providerId;

    @Schema(description = "模型名称；手动模式必填，快捷复制模式可省略", example = "deepseek-v3")
    private String modelName;

    @Schema(description = "模型展示名；手动模式可选，快捷复制模式默认取源模型展示名", example = "DeepSeek V3")
    private String displayName;

    @Schema(description = "模型能力，如 CHAT/EMBEDDING/SPARSE_EMBEDDING；手动模式必填", example = "CHAT")
    private String capability;

    @Schema(description = "调用协议；手动模式必填，合法值 openai/anthropic/google/jina/dashscope", example = "openai")
    private String protocol;

    @Schema(description = "调用入口完整端点 URL；手动模式必填", example = "https://api.deepseek.com/v1/chat/completions")
    private String apiBaseUrl;

    @NotBlank(message = "平台 Key 不能为空")
    @Schema(description = "平台 Key（明文，入库前加密）", example = "sk-platform-xxxxx")
    private String apiKey;

    @Schema(description = "是否设为该能力的系统兜底默认配置", example = "false")
    private Boolean isDefault;
}
