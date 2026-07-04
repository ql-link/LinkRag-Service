package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部模型目录同步任务。
 */
@Data
@TableName("llm_provider_model_sync_job")
@Schema(description = "外部模型目录同步任务")
public class ProviderModelSyncJob {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("provider_id")
    @Schema(description = "厂商ID")
    private Long providerId;

    @TableField("sync_source")
    @Schema(description = "同步来源，如 MODELS_DEV")
    private String syncSource;

    @Schema(description = "任务状态：RUNNING/SUCCESS/FAILED")
    private String status;

    @TableField("added_count")
    @Schema(description = "新增候选数量")
    private Integer addedCount;

    @TableField("updated_count")
    @Schema(description = "匹配既有正式目录的候选数量")
    private Integer updatedCount;

    @TableField("stale_count")
    @Schema(description = "外部源未再出现的本地正式目录数量")
    private Integer staleCount;

    @TableField("error_message")
    @Schema(description = "失败原因")
    private String errorMessage;

    @TableField("started_at")
    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
