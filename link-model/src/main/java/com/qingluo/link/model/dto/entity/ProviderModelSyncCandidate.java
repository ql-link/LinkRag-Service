package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 外部模型目录候选项。
 */
@Data
@TableName("llm_provider_model_sync_candidate")
@Schema(description = "外部模型目录候选项")
public class ProviderModelSyncCandidate {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("job_id")
    @Schema(description = "同步任务ID")
    private Long jobId;

    @TableField("provider_id")
    @Schema(description = "厂商ID")
    private Long providerId;

    @TableField("sync_source")
    @Schema(description = "同步来源")
    private String syncSource;

    @TableField("external_model_id")
    @Schema(description = "外部源模型ID")
    private String externalModelId;

    @TableField("model_name")
    @Schema(description = "拟发布模型名")
    private String modelName;

    @TableField("display_name")
    @Schema(description = "模型展示名")
    private String displayName;

    @TableField("inferred_capability")
    @Schema(description = "推断能力")
    private String inferredCapability;

    @JsonProperty("capability")
    @Schema(description = "能力字段兼容别名；等同 inferredCapability")
    public String getCapability() {
        return inferredCapability;
    }

    @TableField("inferred_protocol")
    @Schema(description = "推断协议")
    private String inferredProtocol;

    @TableField("inferred_api_base_url")
    @Schema(description = "推断调用入口")
    private String inferredApiBaseUrl;

    @TableField("context_window")
    @Schema(description = "上下文窗口")
    private Integer contextWindow;

    @TableField("max_output_tokens")
    @Schema(description = "最大输出 token")
    private Integer maxOutputTokens;

    @TableField("model_release_date")
    @Schema(description = "外部源提供的模型发布日期")
    private LocalDate releaseDate;

    @TableField("input_modalities")
    @Schema(description = "输入模态 JSON")
    private String inputModalities;

    @TableField("output_modalities")
    @Schema(description = "输出模态 JSON")
    private String outputModalities;

    @TableField("raw_metadata")
    @Schema(description = "外部源原始元数据 JSON")
    private String rawMetadata;

    @TableField("review_status")
    @Schema(description = "审核状态：PENDING/PUBLISHED/REJECTED")
    private String reviewStatus;

    @TableField("matched_provider_model_id")
    @Schema(description = "匹配到的正式目录ID")
    private Long matchedProviderModelId;

    @TableField("last_seen_at")
    @Schema(description = "外部源最后发现时间")
    private LocalDateTime lastSeenAt;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
