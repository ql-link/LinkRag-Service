package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统预设表
 * 对应表：llm_system_preset
 *
 * <p>管理员预配的系统兜底模型配置，自带平台 Key（加密）。用户没有自配生效模型时，
 * Java 按能力回退到本表 active + default 的 LinkRag 系统预设。与 llm_user_config 字段对齐：
 * provider_type/protocol/api_base_url 自带事实字段（创建预设时从 llm_provider_model 复制）。</p>
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

    @Schema(description = "模型展示名；为空时前端回退模型名称", example = "GPT-4o")
    @TableField("display_name")
    private String displayName;

    @Schema(description = "能力标识", example = "CHAT")
    @TableField("capability")
    private String capability;

    /** 厂商类型快照：系统兜底解析直接读取，不再回查厂商表补齐运行字段。 */
    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    /** 复制自模型能力层事实，作为系统兜底运行快照。 */
    @Schema(description = "调用协议", example = "openai")
    @TableField("protocol")
    private String protocol;

    /** 复制自模型能力层事实，作为系统兜底运行快照。 */
    @Schema(description = "调用入口基地址", example = "https://api.openai.com/v1")
    @TableField("api_base_url")
    private String apiBaseUrl;

    @Schema(description = "平台 Key（加密）")
    @TableField("api_key")
    private String apiKey;

    @Schema(description = "是否启用为系统兜底候选", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "是否为该能力的系统兜底默认配置", example = "false")
    @TableField("is_default")
    private Boolean isDefault = false;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
