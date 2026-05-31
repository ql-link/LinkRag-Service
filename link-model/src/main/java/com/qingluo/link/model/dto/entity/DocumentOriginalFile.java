package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("document_original_file")
public class DocumentOriginalFile {

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

    @TableField("failure_reason")
    private String failureReason;

    /** 逻辑删除标记：软删保留原文件（隐性删除）；@TableLogic 让读查询自动过滤、delete 转 update，不物理删行、不删 OSS 对象。 */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted = false;

    /** 删除判别列：活行恒为 0、软删时置为自身 id；纳入唯一键使死行退出“活名额”，支持删后同名重传。 */
    @TableField("deleted_seq")
    private Long deletedSeq = 0L;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
