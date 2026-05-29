package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文件级解析聚合记录，与 Python 端 document_parse_file 契约一致。
 */
@Data
@TableName("document_parse_file")
public class DocumentParseFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_original_file_id")
    private Long documentOriginalFileId;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("user_id")
    private Long userId;

    @TableField("latest_parse_task_id")
    private String latestParseTaskId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("parse_count")
    private Integer parseCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
