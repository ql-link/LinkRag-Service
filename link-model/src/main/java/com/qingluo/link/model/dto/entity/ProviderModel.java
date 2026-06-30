package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 厂商模型能力目录表
 * 对应表：llm_provider_model
 *
 * <p>「厂商→模型→能力」三层的中间层，取代原 llm_system_provider.supported_models JSON。
 * 一个模型支持多种能力时拆成多行（同 model_name 多条 capability）。用户配置厂商时，
 * 按本表展开该厂商全部 (模型,能力) 写入 llm_user_config。</p>
 */
@Data
@TableName("llm_provider_model")
@Schema(description = "厂商模型能力目录")
public class ProviderModel {

    @Schema(description = "主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "厂商ID", example = "1")
    @TableField("provider_id")
    private Long providerId;

    @Schema(description = "模型名称", example = "gpt-4o")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "模型展示名；为空时前端回退模型名称", example = "GPT-4o")
    @TableField("display_name")
    private String displayName;

    @Schema(description = "单能力；一模型多能力=多行", example = "CHAT")
    @TableField("capability")
    private String capability;

    /** 事实来源：本 (模型,能力) 真实调用协议（API 家族），下游按 protocol+capability 选 adapter，必填。 */
    @Schema(description = "调用协议", example = "openai")
    @TableField("protocol")
    private String protocol;

    /** 事实来源：本 (模型,能力) 真实调用入口完整端点，用户配置展开时复制此值，必填。 */
    @Schema(description = "调用入口完整端点 URL", example = "https://api.openai.com/v1/chat/completions")
    @TableField("api_base_url")
    private String apiBaseUrl;

    @Schema(description = "该模型能力是否上架", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
