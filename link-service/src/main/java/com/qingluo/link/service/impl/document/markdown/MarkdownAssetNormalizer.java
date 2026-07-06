package com.qingluo.link.service.impl.document.markdown;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.core.exception.BusinessException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
                String url = command.internalBaseUrl() + "/api/v1/internal/files/" + command.fileId()
                    + "/assets?path=" + urlEncodePath(path);
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
                contentType(entry.getValue())))
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
            return result;
        }
        if (assetRelativePaths == null || assets.size() != assetRelativePaths.size()) {
            throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
        }
        Map<String, String> digests = new LinkedHashMap<>();
        for (int i = 0; i < assets.size(); i++) {
            MultipartFile file = assets.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(assetRelativePaths.get(i));
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
            return result;
        }
        if (paths == null || assets.size() != paths.size()) {
            throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
        }
        for (int i = 0; i < assets.size(); i++) {
            MultipartFile asset = assets.get(i);
            if (asset == null || asset.isEmpty()) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(paths.get(i));
            result.putIfAbsent(normalized, asset);
        }
        return result;
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

    private String contentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    private String urlEncodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
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
        String internalBaseUrl
    ) {
    }

    public record PreparedMarkdown(MarkdownUploadBundle bundle) {
    }

    @FunctionalInterface
    public interface AssetMaterializer {
        Path materialize(MultipartFile file);
    }
}
