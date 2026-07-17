package com.qingluo.link.service.blog;

import java.time.LocalDateTime;

/**
 * 公开博客详情的权威元数据快照，不包含 Markdown 正文。
 */
public record BlogPublicDetailSnapshot(
    Long id,
    String title,
    String slug,
    String summary,
    String contentObjectKey,
    Long coverAssetId,
    String coverPublicUrl,
    String status,
    LocalDateTime publishedAt,
    String etag
) {
}
