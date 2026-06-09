package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "公开博客文章详情")
public class BlogPostPublicDetailDTO {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private Long coverAssetId;
    private String coverPublicUrl;
    private LocalDateTime publishedAt;
    private String contentMarkdown;
}
