package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "更新反馈处理状态请求")
public class UpdateFeedbackStatusRequest {

    @NotBlank(message = "反馈状态不能为空")
    @Schema(description = "反馈处理状态：PENDING=待处理，PROCESSING=处理中，RESOLVED=已解决，CLOSED=已关闭", required = true)
    private String status;
}
