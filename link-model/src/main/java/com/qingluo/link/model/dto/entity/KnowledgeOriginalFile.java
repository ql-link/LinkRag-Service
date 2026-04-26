package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 原文件数据库实体。
 *
 * <p>该实体只映射 document_original_file 表，职责是记录用户上传的原始文件事实：
 * 属于哪个用户和数据集、原文件名与后缀、对象存储位置、上传状态和失败原因。
 *
 * <p>解析任务状态、解析进度、解析产物不应继续扩展到这张表。
 * 二期会通过解析任务表和解析产物表承载，保持“原文件”和“解析结果”职责分离。
 */
@Data
@TableName("document_original_file")
public class KnowledgeOriginalFile {

    /** 原文件记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件所属数据集 ID。 */
    @TableField("dataset_id")
    private Long datasetId;

    /** 文件上传用户 ID；与数据集、文件名、后缀共同参与唯一约束。 */
    @TableField("user_id")
    private Long userId;

    /** 用户上传时的原始文件名，大小写敏感。 */
    @TableField("original_filename")
    private String originalFilename;

    /** 文件后缀，小写存储；与文件名一起判断是否为同一份文件。 */
    @TableField("file_suffix")
    private String fileSuffix;

    /** 原文件大小，单位字节。 */
    @TableField("file_size")
    private Long fileSize;

    /** 浏览器上传时携带的 Content-Type，用于内部下载时尽量还原响应类型。 */
    @TableField("content_type")
    private String contentType;

    /** 业务桶名称；一期原文件固定写入 rag-raw。 */
    @TableField("bucket_name")
    private String bucketName;

    /** MinIO 对象 key，是服务端定位私有对象的稳定标识。 */
    @TableField("object_key")
    private String objectKey;

    /** Java 内部下载地址，不是 MinIO 公网地址。 */
    @TableField("file_url")
    private String fileUrl;

    /** 上传状态：uploading、success、failed。 */
    @TableField("upload_status")
    private String uploadStatus;

    /** 是否已成功写入对象存储并完成成功状态回写。 */
    @TableField("is_upload_success")
    private Boolean isUploadSuccess;

    /**
     * 下面几个解析投递字段已从一期原文件表移除。
     * 暂时保留为非表字段，是为了让二期前的旧解析代码仍可编译；二期会改为解析任务表承载。
     */
    @TableField(exist = false)
    private String parseNoticeStatus;

    @TableField(exist = false)
    private String parseTaskId;

    /** 上传失败原因；失败重试时会被清空并重新进入 uploading。 */
    @TableField("failure_reason")
    private String failureReason;

    @TableField(exist = false)
    private Integer parseNoticeRetryCount;

    @TableField(exist = false)
    private LocalDateTime lastParseNoticeAt;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 记录更新时间；上传超时补偿会基于该字段判断 uploading 是否过期。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
