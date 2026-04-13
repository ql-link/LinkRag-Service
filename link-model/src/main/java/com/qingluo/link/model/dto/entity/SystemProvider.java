package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LLM 系统级厂商配置表
 * 对应表：llm_system_provider
 */
@Data
@TableName("llm_system_provider")
public class SystemProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("provider_type")
    private String providerType;

    @TableField("provider_name")
    private String providerName;

    @TableField("api_base_url")
    private String apiBaseUrl;

    @TableField("supported_models")
    private String supportedModels;

    @TableField("config_schema")
    private String configSchema;

    @TableField("is_active")
    private Boolean isActive = true;

    private Integer priority = 50;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}