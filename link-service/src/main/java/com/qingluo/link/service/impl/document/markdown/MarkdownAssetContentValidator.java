package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarkdownAssetContentValidator {

    private static final Map<String, String> EXTENSION_MIME = Map.ofEntries(
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("tif", "image/tiff"),
        Map.entry("tiff", "image/tiff")
    );

    private final DocumentFileProperties properties;

    public MarkdownAssetContentValidator(DocumentFileProperties properties) {
        this.properties = properties;
    }

    public ValidatedAsset validate(MarkdownAssetFile asset) {
        if (asset.sizeBytes() > properties.getMarkdownAssetMaxBytes()) {
            throw new BusinessException(ErrorCode.ASSET_FILE_SIZE_LIMIT_EXCEEDED);
        }
        String declaredExtension = suffix(asset.canonicalPath());
        String expectedMime = EXTENSION_MIME.get(declaredExtension);
        if (expectedMime == null) {
            throw new BusinessException(ErrorCode.IMAGE_CONTENT_MISMATCH);
        }
        DetectedImage detected = detect(asset);
        if (!expectedMime.equals(detected.mimeType())) {
            throw new BusinessException(ErrorCode.IMAGE_CONTENT_MISMATCH);
        }
        String declaredMime = normalizeMime(asset.declaredContentType());
        if (StringUtils.hasText(declaredMime) && !"application/octet-stream".equals(declaredMime)
            && !detected.mimeType().equals(declaredMime)) {
            throw new BusinessException(ErrorCode.IMAGE_CONTENT_MISMATCH);
        }
        return new ValidatedAsset(
            asset,
            detected.canonicalExtension(),
            detected.mimeType(),
            sha256(asset),
            asset.sizeBytes());
    }

    private DetectedImage detect(MarkdownAssetFile asset) {
        byte[] bytes = new byte[16];
        int size;
        try (InputStream input = java.nio.file.Files.newInputStream(asset.tempFile())) {
            size = input.read(bytes);
        } catch (IOException e) {
            throw new BusinessException(400, "配套图片读取失败", 400);
        }
        if (size >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) {
            return new DetectedImage("jpg", "image/jpeg");
        }
        if (size >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
            && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
            && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new DetectedImage("png", "image/png");
        }
        if (size >= 6) {
            String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return new DetectedImage("gif", "image/gif");
            }
        }
        if (size >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
            && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return new DetectedImage("webp", "image/webp");
        }
        if (size >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return new DetectedImage("bmp", "image/bmp");
        }
        if (size >= 4
            && ((bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 42 && bytes[3] == 0)
                || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0 && bytes[3] == 42))) {
            return new DetectedImage("tiff", "image/tiff");
        }
        throw new BusinessException(ErrorCode.IMAGE_CONTENT_MISMATCH);
    }

    private String sha256(MarkdownAssetFile asset) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = java.nio.file.Files.newInputStream(asset.tempFile())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new BusinessException(400, "配套图片读取失败", 400);
        }
    }

    private String suffix(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int semicolon = value.indexOf(';');
        return (semicolon < 0 ? value : value.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    public record ValidatedAsset(
        MarkdownAssetFile source,
        String canonicalExtension,
        String mimeType,
        String sha256,
        long sizeBytes
    ) {
    }

    private record DetectedImage(String canonicalExtension, String mimeType) {
    }
}
