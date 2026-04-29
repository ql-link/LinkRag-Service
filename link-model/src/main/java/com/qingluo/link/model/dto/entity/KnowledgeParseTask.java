package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文件解析日志实体。
 *
 * <p>一条记录代表一次解析尝试。Java 只负责生成 task_id、更新 latest_parse_task_id 并投递 MQ；
 * Python 负责创建并推进 document_parse_log 记录，写入解析时间与失败原因。
 */
@Data
@TableName("document_parse_log")
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

    /** 历史兼容字段，当前默认不走 Java 侧补偿。 */
    @TableField("dispatch_retry_count")
    private Integer dispatchRetryCount;

    /** 历史兼容字段，保留异常摘要用于排查。 */
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
