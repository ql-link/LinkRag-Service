package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "博客文章资源")
public class BlogAssetDTO {

    private Long id;
    private Long postId;
    private String assetType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String objectKey;
    private String publicUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
