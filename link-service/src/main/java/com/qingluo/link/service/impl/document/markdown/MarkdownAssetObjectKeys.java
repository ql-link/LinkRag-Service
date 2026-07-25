package com.qingluo.link.service.impl.document.markdown;

import java.util.Locale;

public final class MarkdownAssetObjectKeys {

    public static final String PREFIX = "markdown-assets/v1/";

    private MarkdownAssetObjectKeys() {
    }

    public static boolean isMarkdown(String suffix) {
        if (suffix == null) {
            return false;
        }
        String value = suffix.toLowerCase(Locale.ROOT);
        return "md".equals(value) || "markdown".equals(value);
    }

    public static boolean isV1NormalizedSource(String objectKey) {
        return objectKey != null && objectKey.startsWith(PREFIX)
            && objectKey.endsWith("/source/normalized.md");
    }

    public static String basePrefix(Long userId, Long datasetId, Long fileId) {
        return PREFIX + "user-%d/dataset-%d/file-%d/".formatted(userId, datasetId, fileId);
    }

    public static String originalKey(Long userId, Long datasetId, Long fileId) {
        return basePrefix(userId, datasetId, fileId) + "source/original.md";
    }

    public static String normalizedKey(Long userId, Long datasetId, Long fileId) {
        return basePrefix(userId, datasetId, fileId) + "source/normalized.md";
    }

    public static String imageFilename(String sha256, String extension) {
        return "image-" + sha256.toLowerCase(Locale.ROOT) + "." + extension.toLowerCase(Locale.ROOT);
    }

    public static String imageKey(Long userId, Long datasetId, Long fileId, String storedFilename) {
        return basePrefix(userId, datasetId, fileId) + "images/" + storedFilename;
    }

    public static String manifestKey(Long userId, Long datasetId, Long fileId) {
        return basePrefix(userId, datasetId, fileId) + "manifest.json";
    }

    public static String manifestKeyFromNormalized(String normalizedObjectKey) {
        if (!isV1NormalizedSource(normalizedObjectKey)) {
            throw new IllegalArgumentException("not a markdown-assets v1 source");
        }
        return normalizedObjectKey.substring(
            0, normalizedObjectKey.length() - "source/normalized.md".length()) + "manifest.json";
    }

    public static String logicalUri(String imageObjectKey) {
        return "tolink-raw://raw/" + imageObjectKey;
    }
}
