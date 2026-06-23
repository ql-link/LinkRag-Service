package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量查询召回 Chunk 详情请求。
 */
@Data
@Schema(description = "批量查询召回 Chunk 详情请求")
public class BatchChunkDetailRequest {

    @NotEmpty(message = "chunkIds 不能为空")
    @Size(max = 100, message = "chunkIds 单次最多查询 100 个")
    @Schema(description = "Chunk 业务唯一键列表", example = "[\"chunk-1\", \"chunk-2\"]")
    private List<String> chunkIds;
}
