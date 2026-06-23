package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档 Chunk 真值记录表。由 Python RAG 端写入，Java 端用于历史召回片段详情只读查询。
 */
@Data
@TableName("kb_document_chunk")
public class KbDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chunk_id")
    private String chunkId;

    @TableField("doc_id")
    private Long docId;

    @TableField("set_id")
    private Long setId;

    @TableField("user_id")
    private Long userId;

    @TableField("bucket_id")
    private Integer bucketId;

    private String content;

    @TableField("content_hash")
    private String contentHash;

    @TableField("chunk_type")
    private String chunkType;

    @TableField("start_line")
    private Integer startLine;

    @TableField("end_line")
    private Integer endLine;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("dense_vector_status")
    private String denseVectorStatus;

    @TableField("dense_vector_model")
    private String denseVectorModel;

    @TableField("sparse_vector_status")
    private String sparseVectorStatus;

    @TableField("sparse_vector_model")
    private String sparseVectorModel;

    @TableField("es_status")
    private String esStatus;

    @TableField("lifecycle_status")
    private String lifecycleStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
