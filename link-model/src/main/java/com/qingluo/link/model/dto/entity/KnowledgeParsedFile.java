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

    /** 最新成功解析任务 ID，对应 document_parse_task.task_id。 */
    @TableField("latest_success_task_id")
    private String latestSuccessTaskId;

    @TableField("original_filename")
    private String originalFilename;

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

    /** 当前原文件累计成功解析次数，只在解析成功后递增。 */
    @TableField("parse_count")
    private Integer parseCount;

    @TableField("parsed_at")
    private LocalDateTime parsedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /*
     * 以下字段是一期前旧解析结果链路的兼容字段，不再映射到二期解析产物表。
     * 保留它们只为避免旧代码在删除前编译失败，二期主链路不要继续写这些字段。
     */
    @TableField(exist = false)
    private String parseTaskId;

    @TableField(exist = false)
    private String parseStatus;

    @TableField(exist = false)
    private Boolean isParseSuccess;

    @TableField(exist = false)
    private String parseResult;

    @TableField(exist = false)
    private String failureReason;

    @TableField(exist = false)
    private LocalDateTime lastResultAt;
}
