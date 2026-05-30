package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端最小召回候选（camelCase）。首版不返回 chunk 正文，也不在 Java 侧回查正文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "召回候选")
public class RecallHitDTO {

    @Schema(description = "chunk 唯一标识", example = "1001")
    private String chunkId;

    @Schema(description = "chunk 所属文档 ID", example = "10")
    private Long docId;

    @Schema(description = "chunk 所属数据集 ID", example = "1")
    private Long datasetId;
}
