package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新博客文章请求")
public class UpdateBlogPostRequest {

    @Size(max = 255, message = "文章标题长度不能超过255")
    @Schema(description = "文章标题")
    private String title;

    @Size(max = 1000, message = "文章摘要长度不能超过1000")
    @Schema(description = "文章摘要")
    private String summary;

    @Schema(description = "封面资源ID")
    private Long coverAssetId;
}
