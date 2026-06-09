package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "管理端博客文章详情")
public class BlogPostAdminDetailDTO {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String contentObjectKey;
    private String contentMarkdown;
    private Long coverAssetId;
    private String status;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
