package com.qingluo.link.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建数据集请求")
public class CreateDatasetRequest {

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称长度不能超过128")
    @Schema(description = "数据集名称", example = "我的数据集")
    private String name;

    @Size(max = 512, message = "数据集描述长度不能超过512")
    @Schema(description = "数据集描述", example = "用于知识问答")
    private String description;

    @NotNull(message = "稀疏向量模型配置不能为空")
    @JsonProperty("sparse_embedding_config_id")
    @JsonAlias("sparseEmbeddingConfigId")
    @Schema(description = "稀疏向量模型配置 ID（llm_user_config.id，能力必须为 SPARSE_EMBEDDING）", example = "10001")
    private Long sparseEmbeddingConfigId;

    @NotNull(message = "稠密向量模型配置不能为空")
    @JsonProperty("dense_embedding_config_id")
    @JsonAlias("denseEmbeddingConfigId")
    @Schema(description = "稠密向量模型配置 ID（llm_user_config.id，能力必须为 EMBEDDING）", example = "10002")
    private Long denseEmbeddingConfigId;
}
