package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.model.dto.response.MarkdownAssetSummaryDTO;
import java.nio.file.Path;
import java.util.List;

public record MarkdownUploadBundle(
    Path originalMarkdown,
    String originalObjectKey,
    List<ImageUpload> images,
    Path normalizedMarkdown,
    String normalizedObjectKey,
    Path manifestFile,
    String manifestObjectKey,
    MarkdownAssetSummaryDTO summary
) {
    public MarkdownUploadBundle {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public boolean hasBlockingIssues() {
        return summary != null && summary.hasBlockingIssues();
    }

    public record ImageUpload(Path tempFile, String objectKey, String contentType) {
    }
}
