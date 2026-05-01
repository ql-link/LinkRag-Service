package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Python 解析事件回调请求。
 *
 * <p>三期后该结构只承接 `processing/progress` 进度事件；
 * `success/failed` 终态结果改由 parse_result MQ 回传给 Java。
 */
@Data
@Schema(description = "Python 解析事件回调请求")
public class KnowledgeParseCallbackRequest {

    /** processing/progress。 */
    @Schema(description = "解析事件类型：processing/progress", example = "progress")
    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    /** 解析进度百分比，progress 事件必填。 */
    @Schema(description = "解析进度百分比，progress 事件必填", example = "80")
    private Integer progress;

    /** 进度链路默认不使用失败原因，保留字段仅为兼容已有 DTO 结构。 */
    @Schema(description = "进度回调默认不使用该字段，保留为兼容结构", example = "null")
    private String failureReason;
}
