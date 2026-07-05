package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户 LLM 配置 DTO
 *
 * <p>对外只暴露脱敏后的 Key。该 DTO 面向前端表达「可使用的配置项」；
 * LinkRag 系统兜底作为只读配置项返回。</p>
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

    @Schema(description = "厂商图标 URL；LinkRag 只读项来自系统厂商表", example = "https://minio.example/tolink-public/providerIcon/linkrag.png")
    private String iconUrl;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "模型展示名；用户自配为空时等于模型名称，LinkRag 只读项来自系统预设", example = "Qwen 3.6 27B")
    private String displayName;

    @Schema(description = "专用能力标识：CHAT/EMBEDDING/SPARSE_EMBEDDING/RERANK", example = "CHAT")
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

    @Schema(description = "历史兼容字段；新用户自配配置恒为 false", example = "false")
    private Boolean isSystemPreset;

    @Schema(description = "是否允许用户编辑/删除/启停", example = "true")
    private Boolean isEditable;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
