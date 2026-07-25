package com.qingluo.link.service.impl.document.markdown;

import java.nio.file.Path;

public record MarkdownAssetFile(
    String canonicalPath,
    String originalFilename,
    Path tempFile,
    String declaredContentType,
    long sizeBytes
) {
}
