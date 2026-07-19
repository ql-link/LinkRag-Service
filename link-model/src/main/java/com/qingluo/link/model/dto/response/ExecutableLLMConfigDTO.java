package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 前端可选择的一条统一 LLM 可执行配置。
 */
@Data
@Schema(description = "统一LLM可执行配置")
public class ExecutableLLMConfigDTO {

    @Schema(description = "唯一配置身份", example = "10001")
    private Long configId;

    @Schema(description = "范围：SYSTEM/USER，仅用于展示和权限", example = "USER")
    private String scope;

    @Schema(description = "厂商ID", example = "10000")
    private Long providerId;

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "厂商图标URL")
    private String iconUrl;

    @Schema(description = "模型名称", example = "gpt-4o")
    private String modelName;

    @Schema(description = "模型展示名", example = "GPT-4o")
    private String displayName;

    @Schema(description = "模型能力", example = "CHAT")
    private String capability;

    @Schema(description = "调用协议", example = "openai")
    private String protocol;

    @Schema(description = "调用入口完整地址")
    private String apiBaseUrl;

    @Schema(description = "脱敏后的API Key")
    private String apiKeyMasked;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "当前用户是否可编辑、启停或删除", example = "true")
    private Boolean editable;

    @Schema(description = "运行快照版本", example = "2")
    private Long snapshotVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
