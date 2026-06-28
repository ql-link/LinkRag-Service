package com.qingluo.link.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import lombok.Data;

/**
 * 全量更新数据集解析/检索配置请求（PUT，整页保存）。
 *
 * <p>四类一次性提交，整行覆盖。各类加 {@code @Valid} 触发嵌套范围校验；某类为 null 表示本次不提供，
 * 落库时该类列写空 {@code {}}，由 Python 消费时补默认（Java 不补字段默认）。
 */
@Data
@Schema(description = "数据集解析/检索配置全量更新请求")
public class UpdateDatasetParseConfigRequest {

    @JsonProperty("sparse_embedding_config_id")
    @JsonAlias("sparseEmbeddingConfigId")
    @Schema(description = "稀疏向量模型配置 ID（llm_user_config.id，能力必须为 SPARSE_EMBEDDING）；不传则保留原绑定")
    private Long sparseEmbeddingConfigId;

    @JsonProperty("dense_embedding_config_id")
    @JsonAlias("denseEmbeddingConfigId")
    @Schema(description = "稠密向量模型配置 ID（llm_user_config.id，能力必须为 EMBEDDING）；不传则保留原绑定")
    private Long denseEmbeddingConfigId;

    @Valid
    @Schema(description = "分块策略配置")
    private ChunkingConfig chunking;

    @Valid
    @Schema(description = "Markdown 增强配置")
    private EnhancementConfig enhancement;

    @Valid
    @Schema(description = "PDF 解析配置")
    private PdfConfig pdf;

    @Valid
    @Schema(description = "召回检索配置")
    private RecallConfig recall;
}
