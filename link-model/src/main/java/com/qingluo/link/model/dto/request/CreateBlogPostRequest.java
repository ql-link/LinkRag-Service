package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建博客文章请求")
public class CreateBlogPostRequest {

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 255, message = "文章标题长度不能超过255")
    @Schema(description = "文章标题", example = "MinIO 存储说明")
    private String title;

    @NotBlank(message = "slug不能为空")
    @Size(max = 100, message = "slug长度不能超过100")
    @Schema(description = "公开访问标识", example = "minio-storage-guide")
    private String slug;

    @Size(max = 1000, message = "文章摘要长度不能超过1000")
    @Schema(description = "文章摘要")
    private String summary;
}
