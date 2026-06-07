package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 召回 session token 签发请求（LINK-104）。
 *
 * <p>{@code datasetIds} <b>必须显式非空</b>：每个 id 为当前用户有权访问的数据集。本接口<b>不沿用</b>内部召回
 * 链路「空列表 = 本人全部库」的展开约定——空列表/缺省直接 400，避免下发空 {@code dataset_ids} claim 被
 * Python 误判为「全库授权」造成越权放大。</p>
 *
 * <p>本接口只负责签发短期 token，<b>不需要 query</b>（query 在前端直连 Python 时随 stream 请求体提交）。</p>
 */
@Data
@Schema(description = "召回 session token 签发请求")
public class RecallSessionRequest {

    @NotEmpty(message = "datasetIds 不能为空，必须显式指定本次授权的数据集")
    @Schema(description = "本次授权的数据集 id 列表（必须显式非空，需为当前用户有权访问的库）", example = "[1, 2]")
    private List<Long> datasetIds;
}
