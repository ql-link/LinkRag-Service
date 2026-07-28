package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量发布同一外部模型的多个能力候选请求。
 */
@Data
@Schema(description = "批量发布同一外部模型的多个能力候选请求")
public class PublishModelSyncCandidatesRequest {

    @NotEmpty
    @Schema(description = "待发布的能力候选ID；必须属于同一厂商和同一外部模型", example = "[101, 102]")
    private List<@NotNull Long> candidateIds;

    @Schema(description = "发布时统一覆盖模型名；为空使用候选模型名", example = "gpt-4o")
    private String modelName;

    @Schema(description = "发布时统一覆盖展示名；为空使用候选展示名", example = "GPT-4o")
    private String displayName;
}
