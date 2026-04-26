package com.qingluo.link.model.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Python 解析事件回调请求。
 *
 * <p>进度和最终事件统一走该结构；Java 只负责推送给浏览器，任务状态最终以 Python 写库为准。
 */
@Data
public class KnowledgeParseCallbackRequest {

    /** processing/progress/success/failed。 */
    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    /** 解析进度百分比，progress 事件必填。 */
    private Integer progress;

    /** Python 返回的业务化失败原因。 */
    private String failureReason;
}
