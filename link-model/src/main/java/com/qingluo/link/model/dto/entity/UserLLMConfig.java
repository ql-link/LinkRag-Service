package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户级 LLM 配置表
 * 对应表：llm_user_config
 */
@Data
@TableName("llm_user_config")
public class UserLLMConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("provider_type")
    private String providerType;

    @TableField("provider_name")
    private String providerName;

    @TableField("config_name")
    private String configName;

    @TableField("api_key")
    private String apiKey;

    @TableField("custom_api_base_url")
    private String customApiBaseUrl;

    @TableField("model_name")
    private String modelName;

    private Integer priority = 50;

    @TableField("is_active")
    private Boolean isActive = true;

    @TableField("is_default")
    private Boolean isDefault = false;

    @TableField("timeout_ms")
    private Integer timeoutMs = 60000;

    @TableField("max_retries")
    private Integer maxRetries = 3;

    @TableField("stream_enabled")
    private Boolean streamEnabled = true;

    private String capabilities;

    @TableField("extra_config")
    private String extraConfig;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}