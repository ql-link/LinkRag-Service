package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档后处理流水线记录（含稀疏向量阶段）。Python 创建并推进，Java 只读。
 *
 * <p>用途：① 用 {@code pipeline_status} 做端到端终态判定（首次/重试/已成功/运行中）；
 * ② 用 {@code superseded_by_task_id} 向后追溯重试链。</p>
 *
 * <p>权威源为 Python ORM DocumentParsePipeline（表 document_parse_pipeline，
 * migration 0007 由 document_post_process_pipeline 改名，0009 增稀疏向量阶段与重试 CAS 列）。
 * 本实体仅建模 Java 读取所需列，刻意不包含已删除的 retry_count / last_retry_at。</p>
 */
@Data
@TableName("document_parse_pipeline")
public class DocumentParsePipeline {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 document_parsed_log.id（唯一）。 */
    @TableField("document_parsed_log_id")
    private Long documentParsedLogId;

    /** 对应 document_parsed_log.task_id（每个 task 一行）。 */
    @TableField("task_id")
    private String taskId;

    @TableField("document_original_file_id")
    private Long documentOriginalFileId;

    @TableField("document_parse_file_id")
    private Long documentParseFileId;

    /** 端到端终态权威列，大写枚举 PENDING/PROCESSING/SUCCESS/FAILED，见 ParsePipelineStatus。 */
    @TableField("pipeline_status")
    private String pipelineStatus;

    /** 失败阶段（CLEANING/.../SPARSE_VECTORIZING/RETRY_VALIDATION），仅审计。 */
    @TableField("failed_stage")
    private String failedStage;

    @TableField("recover_from_stage")
    private String recoverFromStage;

    @TableField("failure_reason")
    private String failureReason;

    /**
     * 重试链向后指针：本（旧）行被哪个新 task_id 接班。NULL=未被重试占用。
     * 由 Python 用 CAS 写入，Java 只读用于向后追溯。
     */
    @TableField("superseded_by_task_id")
    private String supersededByTaskId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
