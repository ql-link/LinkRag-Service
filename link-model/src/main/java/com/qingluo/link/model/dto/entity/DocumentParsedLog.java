package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 每次解析任务日志。Python 创建并推进该记录，Java 仅读取校验和查询。
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

    @TableField("task_status")
    private String taskStatus;

    @TableField("failure_reason")
    private String failureReason;

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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
