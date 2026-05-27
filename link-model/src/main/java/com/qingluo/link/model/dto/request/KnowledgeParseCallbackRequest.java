package com.qingluo.link.model.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Python 解析过程事件请求，终态由 parse_result MQ 承载。
 */
@Data
public class KnowledgeParseCallbackRequest {

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    private Integer progress;
}
