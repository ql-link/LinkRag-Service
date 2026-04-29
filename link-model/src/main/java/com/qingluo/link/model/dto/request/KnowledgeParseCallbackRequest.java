package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Python 解析事件回调请求。
 *
 * <p>进度和最终事件统一走该结构；Java 只负责推送给浏览器，任务状态最终以 Python 写库为准。
 */
@Data
@Schema(description = "Python 解析事件回调请求")
public class KnowledgeParseCallbackRequest {

    /** processing/progress/success/failed。 */
    @Schema(description = "解析事件类型：processing/progress/success/failed", example = "progress")
    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    /** 解析进度百分比，progress 事件必填。 */
    @Schema(description = "解析进度百分比，progress 事件必填", example = "80")
    private Integer progress;

    /** Python 返回的业务化失败原因。 */
    @Schema(description = "Python 返回的业务化失败原因，非失败事件为空", example = "PARSE_TIMEOUT")
    private String failureReason;
}
