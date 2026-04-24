package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("document_original_file")
public class KnowledgeOriginalFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("user_id")
    private Long userId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("file_suffix")
    private String fileSuffix;

    @TableField("file_size")
    private Long fileSize;

    @TableField("content_type")
    private String contentType;

    @TableField("bucket_name")
    private String bucketName;

    @TableField("object_key")
    private String objectKey;

    @TableField("file_url")
    private String fileUrl;

    @TableField("upload_status")
    private String uploadStatus;

    @TableField("is_upload_success")
    private Boolean isUploadSuccess;

    @TableField("parse_notice_status")
    private String parseNoticeStatus;

    @TableField("parse_task_id")
    private String parseTaskId;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("parse_notice_retry_count")
    private Integer parseNoticeRetryCount;

    @TableField("last_parse_notice_at")
    private LocalDateTime lastParseNoticeAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
