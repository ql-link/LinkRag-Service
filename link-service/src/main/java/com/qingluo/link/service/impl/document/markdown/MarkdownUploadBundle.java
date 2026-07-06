package com.qingluo.link.service.impl.document.markdown;

import java.nio.file.Path;
import java.util.List;

public record MarkdownUploadBundle(
    Path originalMarkdown,
    String originalObjectKey,
    Path normalizedMarkdown,
    String normalizedObjectKey,
    Path manifestFile,
    String manifestObjectKey,
    List<AssetFile> assets,
    MarkdownAssetManifest manifest
) {
    public MarkdownUploadBundle {
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public boolean hasMissingAssets() {
        return manifest != null && manifest.hasMissingAssets();
    }

    public record AssetFile(Path tempFile, String objectKey, String contentType) {
    }
}
