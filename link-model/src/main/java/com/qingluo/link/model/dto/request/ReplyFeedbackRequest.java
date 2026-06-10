package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员回复反馈请求")
public class ReplyFeedbackRequest {

    @NotBlank(message = "管理员回复不能为空")
    @Size(max = 5000, message = "管理员回复不能超过 5000 个字符")
    @Schema(description = "管理员回复", required = true)
    private String reply;
}
