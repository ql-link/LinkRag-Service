package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "保存博客Markdown正文请求")
public class SaveBlogContentRequest {

    @NotBlank(message = "Markdown正文不能为空")
    @Schema(description = "完整Markdown正文")
    private String contentMarkdown;
}
