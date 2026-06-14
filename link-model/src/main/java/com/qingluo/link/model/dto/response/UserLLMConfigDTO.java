package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 DTO
 *
 * <p>对外只暴露脱敏后的 Key；is_system_preset 标识只读预设行。
 * 执行参数（超时/重试/流式）等已随重构移除。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户LLM配置")
public class UserLLMConfigDTO {

    @Schema(description = "配置ID", example = "1")
    private Long id;

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "专用能力标识：CHAT/EMBEDDING/RERANK/OCR", example = "CHAT")
    private String capability;

    @Schema(description = "API Key(脱敏)")
    private String apiKeyMasked;

    @Schema(description = "API地址（复制自模型能力层事实）")
    private String apiBaseUrl;

    @Schema(description = "调用协议（运行快照）", example = "openai")
    private String protocol;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "是否为该能力的生效配置", example = "false")
    private Boolean isDefault;

    @Schema(description = "是否为系统预设行（只读）", example = "false")
    private Boolean isSystemPreset;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
