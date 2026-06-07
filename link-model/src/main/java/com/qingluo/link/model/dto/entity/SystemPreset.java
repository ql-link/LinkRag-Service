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
 * 复制进 llm_user_config（is_system_preset=true），实现开箱即用。provider_type/api_base_url
 * 在复制时由 provider_id join llm_system_provider 取得，本表不冗余存储。</p>
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
