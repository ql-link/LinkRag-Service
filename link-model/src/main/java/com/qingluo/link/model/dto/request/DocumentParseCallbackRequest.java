package com.qingluo.link.model.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Python 解析过程事件请求；终态由 Python 写库，前端通过 parse-results 查询。
 */
@Data
public class DocumentParseCallbackRequest {

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    private Integer progress;
}
