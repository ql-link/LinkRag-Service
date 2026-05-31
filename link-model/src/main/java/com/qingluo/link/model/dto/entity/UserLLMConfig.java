package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户级 LLM 配置表
 * 对应表：llm_user_config
 */
@Data
@TableName("llm_user_config")
@Schema(description = "用户LLM配置")
public class UserLLMConfig {

    @Schema(description = "配置ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "厂商ID", example = "1")
    @TableField("provider_id")
    private Long providerId;

    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    @TableField("provider_name")
    private String providerName;

    @Schema(description = "配置名称", example = "我的OpenAI配置")
    @TableField("config_name")
    private String configName;

    @Schema(description = "API Key")
    @TableField("api_key")
    private String apiKey;

    @Schema(description = "自定义API地址")
    @TableField("custom_api_base_url")
    private String customApiBaseUrl;

    @Schema(description = "模型名称", example = "gpt-4")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "优先级", example = "50")
    private Integer priority = 50;

    @Schema(description = "是否启用", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "是否为默认配置", example = "false")
    @TableField("is_default")
    private Boolean isDefault = false;

    @Schema(description = "超时时间(毫秒)", example = "60000")
    @TableField("timeout_ms")
    private Integer timeoutMs = 60000;

    @Schema(description = "最大重试次数", example = "3")
    @TableField("max_retries")
    private Integer maxRetries = 3;

    @Schema(description = "是否启用流式响应", example = "true")
    @TableField("stream_enabled")
    private Boolean streamEnabled = true;

    @Schema(description = "模型能力", example = "CHAT")
    @TableField("capability")
    private String capability;

    @Schema(description = "额外配置")
    @TableField("extra_config")
    private String extraConfig;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}