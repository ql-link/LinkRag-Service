package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 每次解析任务日志。Python 创建并推进该记录，Java 仅读取校验和查询。
 *
 * <p>注意：端到端终态（原 task_status）与失败原因（原 failure_reason）已迁出本表（Python migration 0007），
 * 现由 document_parse_pipeline.pipeline_status / failure_reason 表达；本表仅保留解析（Markdown）产物快照与重试链向前指针。</p>
 */
@Data
@TableName("document_parsed_log")
public class DocumentParsedLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("document_original_file_id")
    private Long documentOriginalFileId;

    @TableField("document_parse_file_id")
    private Long documentParseFileId;

    @TableField("trigger_mode")
    private String triggerMode;

    @TableField("parsed_filename")
    private String parsedFilename;

    @TableField("parsed_bucket_name")
    private String parsedBucketName;

    @TableField("parsed_object_key")
    private String parsedObjectKey;

    @TableField("parsed_file_url")
    private String parsedFileUrl;

    @TableField("parsed_at")
    private LocalDateTime parsedAt;

    @TableField("parse_started_at")
    private LocalDateTime parseStartedAt;

    @TableField("parse_finished_at")
    private LocalDateTime parseFinishedAt;

    @TableField("parse_duration_ms")
    private Long parseDurationMs;

    /**
     * 重试链向前指针：本轮任务对应的上一轮 task_id；首次解析为 null。
     * 由 Python 在创建重试日志时写入（= parse_task 消息的 previous_task_id），Java 只读用于向 origin 回溯。
     */
    @TableField("retry_of_task_id")
    private String retryOfTaskId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
