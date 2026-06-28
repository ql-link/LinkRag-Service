package com.qingluo.link.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据集解析/检索配置回显（GET）。
 *
 * <p>无配置行时四类均为空对象（{@code {}}），表示未配置——Java 不回落系统默认，默认值由 Python 消费时补。
 * 各类只回显用户配过的字段（值对象 {@code NON_NULL}）。
 */
@Data
@Schema(description = "数据集解析/检索配置")
public class DatasetParseConfigResponse {

    @JsonProperty("sparse_embedding_config_id")
    @Schema(description = "稀疏向量模型配置 ID（llm_user_config.id）")
    private Long sparseEmbeddingConfigId;

    @JsonProperty("dense_embedding_config_id")
    @Schema(description = "稠密向量模型配置 ID（llm_user_config.id）")
    private Long denseEmbeddingConfigId;

    @Schema(description = "分块策略配置")
    private ChunkingConfig chunking;

    @Schema(description = "Markdown 增强配置")
    private EnhancementConfig enhancement;

    @Schema(description = "PDF 解析配置")
    private PdfConfig pdf;

    @Schema(description = "召回检索配置")
    private RecallConfig recall;
}
