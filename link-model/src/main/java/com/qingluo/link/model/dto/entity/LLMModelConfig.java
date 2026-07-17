package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 全局唯一的 LLM 可执行配置快照。
 */
@Data
@TableName("llm_model_config")
@Schema(description = "LLM可执行配置")
public class LLMModelConfig {

    @TableId(type = IdType.AUTO)
    @Schema(description = "全局配置ID", example = "10001")
    private Long id;

    @TableField("scope")
    @Schema(description = "配置范围：SYSTEM 平台配置，USER 用户配置", example = "USER")
    private String scope;

    @TableField("owner_user_id")
    @Schema(description = "所有者用户ID；SYSTEM固定为0", example = "7")
    private Long ownerUserId;

    @TableField("provider_id")
    @Schema(description = "厂商ID", example = "10000")
    private Long providerId;

    @TableField("provider_type")
    @Schema(description = "厂商类型运行快照", example = "openai")
    private String providerType;

    @TableField("model_name")
    @Schema(description = "模型名称", example = "gpt-4o")
    private String modelName;

    @TableField("display_name")
    @Schema(description = "模型展示名", example = "GPT-4o")
    private String displayName;

    @TableField("capability")
    @Schema(description = "能力：CHAT/EMBEDDING/SPARSE_EMBEDDING/VISION/RERANK/ASR", example = "CHAT")
    private String capability;

    @TableField("protocol")
    @Schema(description = "调用协议", example = "openai")
    private String protocol;

    @TableField("api_base_url")
    @Schema(description = "调用入口完整地址")
    private String apiBaseUrl;

    @TableField("api_key")
    @Schema(description = "加密后的API Key")
    private String apiKey;

    @TableField("is_active")
    @Schema(description = "是否允许精确执行", example = "true")
    private Boolean isActive;

    @TableField("snapshot_version")
    @Schema(description = "运行快照版本", example = "2")
    private Long snapshotVersion;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
