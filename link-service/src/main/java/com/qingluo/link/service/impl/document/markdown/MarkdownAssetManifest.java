package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.model.dto.response.MarkdownAssetIssueDTO;
import com.qingluo.link.model.dto.response.MarkdownAssetSummaryDTO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MarkdownAssetManifest {

    private int version = 1;
    private Long userId;
    private Long datasetId;
    private Long fileId;
    private String documentPath;
    private String matchMode;
    private SourceEntry source;
    private List<ImageEntry> images = new ArrayList<>();
    private List<MarkdownAssetIssueDTO> references = new ArrayList<>();
    private MarkdownAssetSummaryDTO summary;
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Data
    public static class SourceEntry {
        private String originalObjectKey;
        private String normalizedObjectKey;
        private String originalSha256;
        private String normalizedSha256;
    }

    @Data
    public static class ImageEntry {
        private String originalFilename;
        private String normalizedPath;
        private String storedFilename;
        private String objectKey;
        private String sha256;
        private String mimeType;
        private long sizeBytes;
    }
}
