package com.qingluo.link.service.impl.document.markdown;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.core.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class MarkdownAssetNormalizer {

    private static final long MAX_ASSET_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> IMAGE_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final MarkdownAssetReferenceScanner scanner = new MarkdownAssetReferenceScanner();

    public PreparedMarkdown prepare(PrepareCommand command) {
        String markdown;
        try {
            markdown = Files.readString(command.markdownTempFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(400, "Markdown 文件读取失败", 400);
        }
        List<MarkdownAssetReference> references = scanner.scan(markdown);
        Map<String, MultipartFile> assets = buildAssetMetadataMap(command.assets(), command.assetRelativePaths());
        Set<String> referenced = new LinkedHashSet<>();
        Map<String, String> rewritten = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (MarkdownAssetReference reference : references) {
            String path = reference.normalizedPath();
            referenced.add(path);
            if (assets.containsKey(path)) {
                String url = assetUrl(command, path);
                rewritten.put(path, url);
            } else {
                missing.add(path);
            }
        }
        String normalizedMarkdown = scanner.rewrite(markdown, rewritten);

        List<MarkdownUploadBundle.AssetFile> assetFiles = assets.entrySet().stream()
            .filter(entry -> referenced.contains(entry.getKey()))
            .map(entry -> new MarkdownUploadBundle.AssetFile(
                command.assetTempFiles().get(entry.getKey()),
                MarkdownAssetObjectKeys.assetKey(command.userId(), command.datasetId(), command.fileId(), entry.getKey()),
                contentType(entry.getKey())))
            .toList();
        MarkdownAssetManifest manifest = new MarkdownAssetManifest(missing, rewritten);
        Path normalizedPath = writeTemp("tolink-md-normalized-", ".md", normalizedMarkdown);
        Path manifestPath = writeTemp("tolink-md-assets-", ".json", JSON.toJSONString(Map.of(
            "missingAssets", manifest.missingAssets(),
            "rewrittenAssets", manifest.rewrittenAssets()
        )));
        MarkdownUploadBundle bundle = new MarkdownUploadBundle(
            command.markdownTempFile(),
            MarkdownAssetObjectKeys.originalKey(command.userId(), command.datasetId(), command.fileId(), command.originalFilename()),
            normalizedPath,
            MarkdownAssetObjectKeys.normalizedKey(command.userId(), command.datasetId(), command.fileId(), command.originalFilename()),
            manifestPath,
            MarkdownAssetObjectKeys.manifestKey(command.userId(), command.datasetId(), command.fileId()),
            assetFiles,
            manifest);
        return new PreparedMarkdown(bundle);
    }

    public Map<String, Path> materializedAssetPaths(List<MultipartFile> assets, List<String> assetRelativePaths,
                                                    AssetMaterializer materializer) {
        Map<String, Path> result = new LinkedHashMap<>();
        if (assets == null || assets.isEmpty()) {
            if (assetRelativePaths != null && !assetRelativePaths.isEmpty()) {
                throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
            }
            return result;
        }
        if (assetRelativePaths == null || assets.size() != assetRelativePaths.size()) {
            throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
        }
        Map<String, String> digests = new LinkedHashMap<>();
        for (int i = 0; i < assets.size(); i++) {
            MultipartFile file = assets.get(i);
            String normalized = MarkdownAssetPathNormalizer.normalize(assetRelativePaths.get(i));
            validateAsset(file, normalized);
            String digest = digest(file);
            String existingDigest = digests.putIfAbsent(normalized, digest);
            if (existingDigest != null && !existingDigest.equals(digest)) {
                throw new BusinessException(400, "配套图片路径冲突", 400);
            }
            if (!result.containsKey(normalized)) {
                result.put(normalized, materializer.materialize(file));
            }
        }
        return result;
    }

    private Map<String, MultipartFile> buildAssetMetadataMap(List<MultipartFile> assets, List<String> paths) {
        Map<String, MultipartFile> result = new LinkedHashMap<>();
        if (assets == null || assets.isEmpty()) {
            if (paths != null && !paths.isEmpty()) {
                throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
            }
            return result;
        }
        if (paths == null || assets.size() != paths.size()) {
            throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
        }
        for (int i = 0; i < assets.size(); i++) {
            MultipartFile asset = assets.get(i);
            String normalized = MarkdownAssetPathNormalizer.normalize(paths.get(i));
            validateAsset(asset, normalized);
            result.putIfAbsent(normalized, asset);
        }
        return result;
    }

    private void validateAsset(MultipartFile file, String normalizedPath) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "配套图片不能为空", 400);
        }
        if (file.getSize() > MAX_ASSET_BYTES) {
            throw new BusinessException(400, "配套图片不能超过10MB", 400);
        }
        String suffix = suffixOf(normalizedPath);
        if (!IMAGE_SUFFIXES.contains(suffix)) {
            throw new BusinessException(400, "配套图片格式不支持", 400);
        }
        String contentType = normalizeContentType(file.getContentType());
        if (StringUtils.hasText(contentType)
                && !"application/octet-stream".equals(contentType)
                && !matchesContentType(suffix, contentType)) {
            throw new BusinessException(400, "配套图片MIME类型不支持", 400);
        }
        String detected = detectImageSuffix(file);
        if (!IMAGE_SUFFIXES.contains(detected) || !sameImageType(suffix, detected)) {
            throw new BusinessException(400, "配套图片内容不是有效图片", 400);
        }
    }

    private String digest(MultipartFile file) {
        try {
            return DigestUtils.md5DigestAsHex(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(400, "配套图片读取失败", 400);
        }
    }

    private Path writeTemp(String prefix, String suffix, String content) {
        try {
            Path path = Files.createTempFile(prefix, suffix);
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }
    }

    private String contentType(String normalizedPath) {
        return switch (suffixOf(normalizedPath)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String urlEncodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String assetUrl(PrepareCommand command, String path) {
        String url = command.internalBaseUrl() + "/api/v1/internal/files/" + command.fileId()
            + "/assets?path=" + urlEncodePath(path);
        if (StringUtils.hasText(command.serviceToken())) {
            url += "&token=" + urlEncodePath(command.serviceToken());
        }
        return url;
    }

    private String suffixOf(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int index = path.lastIndexOf('.');
        if (index < 0 || index == path.length() - 1) {
            return "";
        }
        return path.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String normalized = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesContentType(String suffix, String contentType) {
        return switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg".equals(contentType);
            case "png" -> "image/png".equals(contentType);
            case "gif" -> "image/gif".equals(contentType);
            case "webp" -> "image/webp".equals(contentType);
            default -> false;
        };
    }

    private String detectImageSuffix(MultipartFile file) {
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(12);
        } catch (IOException e) {
            throw new BusinessException(400, "配套图片读取失败", 400);
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return "gif";
            }
        }
        if (bytes.length >= 12) {
            String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return "webp";
            }
        }
        return "";
    }

    private boolean sameImageType(String suffix, String detected) {
        if (("jpg".equals(suffix) || "jpeg".equals(suffix)) && "jpg".equals(detected)) {
            return true;
        }
        return suffix.equals(detected);
    }

    public record PrepareCommand(
        Long userId,
        Long datasetId,
        Long fileId,
        String originalFilename,
        Path markdownTempFile,
        List<MultipartFile> assets,
        List<String> assetRelativePaths,
        Map<String, Path> assetTempFiles,
        String internalBaseUrl,
        String serviceToken
    ) {
    }

    public record PreparedMarkdown(MarkdownUploadBundle bundle) {
    }

    @FunctionalInterface
    public interface AssetMaterializer {
        Path materialize(MultipartFile file);
    }
}
