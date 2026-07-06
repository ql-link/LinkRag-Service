package com.qingluo.link.service.impl.document.markdown;

import java.util.Locale;

public final class MarkdownAssetObjectKeys {

    private MarkdownAssetObjectKeys() {
    }

    public static boolean isMarkdown(String suffix) {
        if (suffix == null) {
            return false;
        }
        String value = suffix.toLowerCase(Locale.ROOT);
        return "md".equals(value) || "markdown".equals(value);
    }

    public static String basePrefix(Long userId, Long datasetId, Long fileId) {
        return "user-%d/dataset-%d/file-%d".formatted(userId, datasetId, fileId);
    }

    public static String originalKey(Long userId, Long datasetId, Long fileId, String filename) {
        return basePrefix(userId, datasetId, fileId) + "/original/" + filename;
    }

    public static String normalizedKey(Long userId, Long datasetId, Long fileId, String filename) {
        return basePrefix(userId, datasetId, fileId) + "/normalized/" + filename;
    }

    public static String assetKey(Long userId, Long datasetId, Long fileId, String relativePath) {
        return basePrefix(userId, datasetId, fileId) + "/assets/" + relativePath;
    }

    public static String manifestKey(Long userId, Long datasetId, Long fileId) {
        return basePrefix(userId, datasetId, fileId) + "/assets-manifest.json";
    }
}
