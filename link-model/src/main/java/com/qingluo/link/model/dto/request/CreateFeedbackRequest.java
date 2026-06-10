package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建匿名反馈请求")
public class CreateFeedbackRequest {

    @Schema(description = "反馈类型：BUG=问题反馈，FEATURE=功能建议，EXPERIENCE=体验反馈，OTHER=其他", example = "BUG")
    private String type;

    @NotBlank(message = "反馈标题不能为空")
    @Size(max = 128, message = "反馈标题不能超过 128 个字符")
    @Schema(description = "反馈标题", required = true)
    private String title;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 5000, message = "反馈内容不能超过 5000 个字符")
    @Schema(description = "反馈内容", required = true)
    private String content;
}
