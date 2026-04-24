package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("document_parsed_file")
public class KnowledgeParsedFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_original_file_id")
    private Long documentOriginalFileId;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("user_id")
    private Long userId;

    @TableField("parse_task_id")
    private String parseTaskId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("parse_status")
    private String parseStatus;

    @TableField("is_parse_success")
    private Boolean isParseSuccess;

    @TableField("parsed_filename")
    private String parsedFilename;

    @TableField("parsed_bucket_name")
    private String parsedBucketName;

    @TableField("parsed_object_key")
    private String parsedObjectKey;

    @TableField("parsed_file_url")
    private String parsedFileUrl;

    @TableField("parsed_storage_path")
    private String parsedStoragePath;

    @TableField("parse_result")
    private String parseResult;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("parsed_at")
    private LocalDateTime parsedAt;

    @TableField("last_result_at")
    private LocalDateTime lastResultAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
