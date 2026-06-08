package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "管理端博客文章列表项")
public class BlogPostAdminListDTO {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String contentObjectKey;
    private Long coverAssetId;
    private String status;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
