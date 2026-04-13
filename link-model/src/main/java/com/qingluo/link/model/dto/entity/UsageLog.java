package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LLM 调用用量日志表
 * 对应表：llm_usage_log
 */
@Data
@TableName("llm_usage_log")
public class UsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("config_id")
    private Long configId;

    @TableField("provider_type")
    private String providerType;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("fallback_config_id")
    private Long fallbackConfigId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}