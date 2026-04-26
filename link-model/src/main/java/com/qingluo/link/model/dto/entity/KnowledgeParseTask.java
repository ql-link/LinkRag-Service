package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文件解析任务实体。
 *
 * <p>一条记录代表一次解析尝试。Java 只负责创建任务、投递 MQ 和处理投递补偿；
 * Python 负责把任务推进到 processing/success/failed，并写入解析时间与失败原因。
 */
@Data
@TableName("document_parse_task")
public class KnowledgeParseTask {

    /** 数据库自增主键，不作为跨系统幂等键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 解析任务业务 ID，贯穿 DB、MQ、Python 回调和日志追踪。 */
    @TableField("task_id")
    private String taskId;

    /** 原文件记录 ID，对应 document_original_file.id。 */
    @TableField("document_original_file_id")
    private Long documentOriginalFileId;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("user_id")
    private Long userId;

    /** 触发方式：upload_auto/manual_retry。 */
    @TableField("trigger_mode")
    private String triggerMode;

    /** 任务状态：created/processing/success/failed。 */
    @TableField("task_status")
    private String taskStatus;

    /** 面向前端展示的业务化失败原因。 */
    @TableField("failure_reason")
    private String failureReason;

    /** MQ 投递补偿次数，只由 Java 维护。 */
    @TableField("dispatch_retry_count")
    private Integer dispatchRetryCount;

    /** 最近一次投递异常摘要，用于排查，不直接暴露给用户。 */
    @TableField("last_dispatch_error")
    private String lastDispatchError;

    @TableField("last_dispatched_at")
    private LocalDateTime lastDispatchedAt;

    @TableField("parse_started_at")
    private LocalDateTime parseStartedAt;

    @TableField("parse_finished_at")
    private LocalDateTime parseFinishedAt;

    @TableField("parse_duration_ms")
    private Long parseDurationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
