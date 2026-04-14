package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LLM 调用用量日志表
 * 对应表：llm_usage_log
 */
@Data
@TableName("llm_usage_log")
@Schema(description = "用量日志")
public class UsageLog {

    @Schema(description = "记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "配置ID", example = "1")
    @TableField("config_id")
    private Long configId;

    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    @Schema(description = "模型名称", example = "gpt-4")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "提示词Token数", example = "100")
    @TableField("prompt_tokens")
    private Integer promptTokens;

    @Schema(description = "补全Token数", example = "50")
    @TableField("completion_tokens")
    private Integer completionTokens;

    @Schema(description = "总Token数", example = "150")
    @TableField("total_tokens")
    private Integer totalTokens;

    @Schema(description = "延迟(毫秒)", example = "150")
    @TableField("latency_ms")
    private Integer latencyMs;

    @Schema(description = "状态", example = "success")
    private String status;

    @Schema(description = "错误信息")
    @TableField("error_message")
    private String errorMessage;

    @Schema(description = "备用配置ID")
    @TableField("fallback_config_id")
    private Long fallbackConfigId;

    @Schema(description = "对话ID", example = "1")
    @TableField("conversation_id")
    private Long conversationId;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;
}