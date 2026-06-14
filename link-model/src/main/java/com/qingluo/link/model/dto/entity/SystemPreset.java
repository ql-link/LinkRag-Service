package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统预设表
 * 对应表：llm_system_preset
 *
 * <p>管理员预配的整套可用配置模板，自带平台 Key（加密）。用户注册时按本表 active 行
 * 复制进 llm_user_config（is_system_preset=true），实现开箱即用。与 llm_user_config 字段对齐：
 * provider_type/protocol/api_base_url 自带事实字段（创建预设时从 llm_provider_model 复制），
 * 镜像时直接平移，不再 join 厂商表补全。</p>
 */
@Data
@TableName("llm_system_preset")
@Schema(description = "系统预设")
public class SystemPreset {

    @Schema(description = "主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "厂商ID", example = "1")
    @TableField("provider_id")
    private Long providerId;

    @Schema(description = "模型名称", example = "gpt-4o")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "能力标识", example = "CHAT")
    @TableField("capability")
    private String capability;

    /** 与用户配置对齐：厂商类型快照，下沉自包含，镜像免 join。 */
    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    /** 与用户配置对齐：复制自模型能力层事实，镜像时平移给 llm_user_config。 */
    @Schema(description = "调用协议", example = "openai")
    @TableField("protocol")
    private String protocol;

    /** 与用户配置对齐：复制自模型能力层事实，镜像时平移给 llm_user_config。 */
    @Schema(description = "调用入口基地址", example = "https://api.openai.com/v1")
    @TableField("api_base_url")
    private String apiBaseUrl;

    @Schema(description = "平台 Key（加密）")
    @TableField("api_key")
    private String apiKey;

    @Schema(description = "是否对新用户下发", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
