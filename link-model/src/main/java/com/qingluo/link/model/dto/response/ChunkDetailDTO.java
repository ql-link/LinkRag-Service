package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 召回 Chunk 详情。
 */
@Data
@Schema(description = "召回 Chunk 详情")
public class ChunkDetailDTO {

    @Schema(description = "Chunk 业务唯一键", example = "chunk-1")
    private String chunkId;

    @Schema(description = "原文档 ID", example = "30001")
    private Long documentId;

    @Schema(description = "原文件名", example = "rag-intro.pdf")
    private String fileName;

    @Schema(description = "Chunk 正文")
    private String content;

    @Schema(description = "匹配分数；历史现查无分数时为 null", example = "0.87")
    private Double score;
}
