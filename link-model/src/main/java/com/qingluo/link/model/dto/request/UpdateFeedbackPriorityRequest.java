package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新反馈优先级请求")
public class UpdateFeedbackPriorityRequest {

    @NotNull(message = "反馈优先级不能为空")
    @Min(value = 1, message = "反馈优先级必须在 1 到 3 之间")
    @Max(value = 3, message = "反馈优先级必须在 1 到 3 之间")
    @Schema(description = "反馈优先级：1=高，2=中，3=低", required = true)
    private Integer priority;
}
