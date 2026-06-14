package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LLM 系统级厂商配置表
 * 对应表：llm_system_provider
 */
@Data
@TableName("llm_system_provider")
@Schema(description = "系统厂商配置")
public class SystemProvider {

    @Schema(description = "厂商ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    @TableField("provider_name")
    private String providerName;

    /** 默认模板：新增模型能力时的预填入口，不参与运行决策（运行事实在 llm_provider_model）。 */
    @Schema(description = "默认API地址", example = "https://api.openai.com/v1")
    @TableField("api_base_url")
    private String apiBaseUrl;

    /** 默认模板：新增模型能力时的预填协议，不参与运行决策。 */
    @Schema(description = "默认协议", example = "openai")
    @TableField("default_protocol")
    private String defaultProtocol;

    @Schema(description = "是否启用", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "优先级", example = "10")
    private Integer priority = 50;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}