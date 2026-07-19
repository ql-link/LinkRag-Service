package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.response.MarkdownAssetIssueDTO;
import com.qingluo.link.model.dto.response.MarkdownAssetSummaryDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetContentValidator.ValidatedAsset;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetPathResolver.Resolution;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetPathResolver.Status;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarkdownAssetPackageProcessor {

    private final DocumentFileProperties properties;
    private final MarkdownAssetReferenceScanner scanner;
    private final MarkdownAssetPathResolver pathResolver;
    private final MarkdownAssetContentValidator contentValidator;
    private final MarkdownAssetManifestStore manifestStore;

    public MarkdownAssetPackageProcessor(
            DocumentFileProperties properties,
            MarkdownAssetReferenceScanner scanner,
            MarkdownAssetPathResolver pathResolver,
            MarkdownAssetContentValidator contentValidator,
            MarkdownAssetManifestStore manifestStore) {
        this.properties = properties;
        this.scanner = scanner;
        this.pathResolver = pathResolver;
        this.contentValidator = contentValidator;
        this.manifestStore = manifestStore;
    }

    public List<MarkdownAssetReference> scan(Path markdownPath) {
        return scanner.scan(readMarkdown(markdownPath));
    }

    public PreflightPlan preflight(
            Path markdownPath,
            String originalFilename,
            MarkdownAssetMatchMode matchMode,
            String requestedDocumentPath,
            List<MarkdownAssetFile> suppliedAssets,
            List<String> suppliedInventory) {
        if (!properties.isMarkdownAssetsEnabled()) {
            throw new BusinessException(400, "Markdown 本地图片导入功能未启用", 400);
        }
        if (matchMode == null) {
            throw new BusinessException(ErrorCode.MARKDOWN_LOCAL_ASSET_REQUIRES_CONTEXT);
        }
        if (suppliedAssets.size() > properties.getMarkdownAssetMaxCount()) {
            throw new BusinessException(ErrorCode.ASSET_COUNT_LIMIT_EXCEEDED);
        }
        if (suppliedInventory.size() > properties.getMarkdownInventoryMaxCount()) {
            throw new BusinessException(ErrorCode.ASSET_COUNT_LIMIT_EXCEEDED);
        }
        long markdownBytes = fileSize(markdownPath);
        long bundleBytes = markdownBytes;
        for (MarkdownAssetFile asset : suppliedAssets) {
            if (asset.sizeBytes() > properties.getMarkdownAssetMaxBytes()) {
                throw new BusinessException(ErrorCode.ASSET_FILE_SIZE_LIMIT_EXCEEDED);
            }
            bundleBytes = Math.addExact(bundleBytes, asset.sizeBytes());
        }
        if (bundleBytes > properties.getMarkdownBundleMaxBytes()) {
            throw new BusinessException(ErrorCode.ASSET_BUNDLE_SIZE_LIMIT_EXCEEDED);
        }

        String documentPath = matchMode == MarkdownAssetMatchMode.SHALLOW_BASENAME
            ? normalizeSingleFilename(originalFilename)
            : pathResolver.normalizeCatalogPath(requestedDocumentPath);
        if (documentPath.length() > properties.getMarkdownDocumentPathMaxLength()) {
            throw new BusinessException(ErrorCode.ASSET_PATH_LENGTH_EXCEEDED);
        }

        Set<String> inventory = normalizeInventory(suppliedInventory, matchMode);
        Map<String, MarkdownAssetFile> assetsByPath = normalizeAssets(suppliedAssets, matchMode);
        inventory.addAll(assetsByPath.keySet());

        String markdown = readMarkdown(markdownPath);
        List<MarkdownAssetReference> references = scanner.scan(markdown);
        List<Resolution> resolutions = pathResolver.resolveAll(
            references, matchMode, documentPath, inventory, assetsByPath);

        Map<String, ValidatedAsset> validatedByPath = new LinkedHashMap<>();
        for (Resolution resolution : resolutions) {
            if (resolution.status() == Status.MATCHED) {
                validatedByPath.computeIfAbsent(
                    resolution.asset().canonicalPath(),
                    ignored -> contentValidator.validate(resolution.asset()));
            }
        }
        MarkdownAssetSummaryDTO summary = buildSummary(matchMode, resolutions);
        return new PreflightPlan(
            markdownPath,
            markdown,
            documentPath,
            matchMode,
            resolutions,
            validatedByPath,
            summary);
    }

    public MarkdownUploadBundle finalizeForFile(
            PreflightPlan plan,
            Long userId,
            Long datasetId,
            Long fileId,
            Path outputDirectory) {
        Map<String, StoredAsset> storedByPath = new LinkedHashMap<>();
        Map<String, StoredAsset> uniqueStored = new LinkedHashMap<>();
        for (Map.Entry<String, ValidatedAsset> entry : plan.validatedByPath().entrySet()) {
            ValidatedAsset validated = entry.getValue();
            String filename = MarkdownAssetObjectKeys.imageFilename(
                validated.sha256(), validated.canonicalExtension());
            String objectKey = MarkdownAssetObjectKeys.imageKey(userId, datasetId, fileId, filename);
            StoredAsset stored = new StoredAsset(validated, filename, objectKey);
            storedByPath.put(entry.getKey(), stored);
            uniqueStored.putIfAbsent(filename, stored);
        }

        List<MarkdownAssetReferenceScanner.ResolvedRewrite> rewrites = new ArrayList<>();
        for (int i = 0; i < plan.resolutions().size(); i++) {
            Resolution resolution = plan.resolutions().get(i);
            if (resolution.status() != Status.MATCHED) {
                continue;
            }
            StoredAsset stored = storedByPath.get(resolution.asset().canonicalPath());
            String logicalUri = MarkdownAssetObjectKeys.logicalUri(stored.objectKey());
            rewrites.add(new MarkdownAssetReferenceScanner.ResolvedRewrite(
                resolution.reference(), logicalUri));
            MarkdownAssetIssueDTO issue = plan.summary().getIssues().get(i);
            issue.setOriginalFilename(stored.validated().source().originalFilename());
            issue.setStoredFilename(stored.storedFilename());
            issue.setLogicalUri(logicalUri);
        }
        String normalizedMarkdown = scanner.rewrite(plan.markdown(), rewrites);
        Path normalizedPath = writeString(outputDirectory, "normalized-", ".md", normalizedMarkdown);

        String originalKey = MarkdownAssetObjectKeys.originalKey(userId, datasetId, fileId);
        String normalizedKey = MarkdownAssetObjectKeys.normalizedKey(userId, datasetId, fileId);
        MarkdownAssetManifest manifest = buildManifest(
            plan, userId, datasetId, fileId, originalKey, normalizedKey, normalizedMarkdown, uniqueStored);
        Path manifestPath = manifestStore.materialize(outputDirectory, manifest);
        List<MarkdownUploadBundle.ImageUpload> images = uniqueStored.values().stream()
            .sorted(Comparator.comparing(StoredAsset::objectKey))
            .map(stored -> new MarkdownUploadBundle.ImageUpload(
                stored.validated().source().tempFile(),
                stored.objectKey(),
                stored.validated().mimeType()))
            .toList();
        return new MarkdownUploadBundle(
            plan.markdownPath(),
            originalKey,
            images,
            normalizedPath,
            normalizedKey,
            manifestPath,
            MarkdownAssetObjectKeys.manifestKey(userId, datasetId, fileId),
            plan.summary());
    }

    private MarkdownAssetManifest buildManifest(
            PreflightPlan plan,
            Long userId,
            Long datasetId,
            Long fileId,
            String originalKey,
            String normalizedKey,
            String normalizedMarkdown,
            Map<String, StoredAsset> uniqueStored) {
        MarkdownAssetManifest manifest = new MarkdownAssetManifest();
        manifest.setUserId(userId);
        manifest.setDatasetId(datasetId);
        manifest.setFileId(fileId);
        manifest.setDocumentPath(plan.documentPath());
        manifest.setMatchMode(plan.matchMode().name());
        manifest.setSummary(plan.summary());
        manifest.setReferences(plan.summary().getIssues());

        MarkdownAssetManifest.SourceEntry source = new MarkdownAssetManifest.SourceEntry();
        source.setOriginalObjectKey(originalKey);
        source.setNormalizedObjectKey(normalizedKey);
        source.setOriginalSha256(sha256(plan.markdownPath()));
        source.setNormalizedSha256(sha256(normalizedMarkdown.getBytes(StandardCharsets.UTF_8)));
        manifest.setSource(source);

        List<MarkdownAssetManifest.ImageEntry> images = new ArrayList<>();
        for (StoredAsset stored : uniqueStored.values()) {
            MarkdownAssetManifest.ImageEntry image = new MarkdownAssetManifest.ImageEntry();
            image.setOriginalFilename(stored.validated().source().originalFilename());
            image.setNormalizedPath(stored.validated().source().canonicalPath());
            image.setStoredFilename(stored.storedFilename());
            image.setObjectKey(stored.objectKey());
            image.setSha256(stored.validated().sha256());
            image.setMimeType(stored.validated().mimeType());
            image.setSizeBytes(stored.validated().sizeBytes());
            images.add(image);
        }
        manifest.setImages(images);
        return manifest;
    }

    private MarkdownAssetSummaryDTO buildSummary(
            MarkdownAssetMatchMode matchMode,
            List<Resolution> resolutions) {
        MarkdownAssetSummaryDTO summary = new MarkdownAssetSummaryDTO();
        summary.setMatchMode(matchMode.name());
        for (Resolution resolution : resolutions) {
            MarkdownAssetIssueDTO issue = new MarkdownAssetIssueDTO();
            issue.setSyntax(resolution.reference().syntax().name());
            issue.setOriginalTarget(resolution.reference().originalTarget());
            issue.setNormalizedTarget(resolution.normalizedTarget());
            issue.setResolution(resolution.status().name());
            issue.setBasenameCandidates(new ArrayList<>(resolution.candidates()));
            issue.setCandidateFilenames(new ArrayList<>(resolution.existingCandidates()));
            switch (resolution.status()) {
                case MATCHED -> {
                    summary.setMatchedCount(summary.getMatchedCount() + 1);
                    issue.setOriginalFilename(resolution.asset().originalFilename());
                }
                case MISSING -> {
                    summary.setMissingCount(summary.getMissingCount() + 1);
                    summary.getMissingPaths().add(resolution.normalizedTarget());
                    issue.setIssueKind("ASSET_MISSING");
                }
                case AMBIGUOUS -> {
                    summary.setAmbiguousCount(summary.getAmbiguousCount() + 1);
                    summary.getCandidateFilenames().addAll(resolution.existingCandidates());
                    issue.setIssueKind("ASSET_AMBIGUOUS");
                }
                case UNSUPPORTED -> {
                    summary.setUnsupportedCount(summary.getUnsupportedCount() + 1);
                    issue.setIssueKind("UNSUPPORTED_IMAGE_TYPE");
                    issue.setOriginalFilename(basename(resolution.normalizedTarget()));
                }
                default -> throw new IllegalStateException("Unexpected resolution");
            }
            summary.getIssues().add(issue);
        }
        summary.setMissingPaths(summary.getMissingPaths().stream().distinct().toList());
        summary.setCandidateFilenames(summary.getCandidateFilenames().stream().distinct().toList());
        summary.setBlockingIssues(
            summary.getMissingCount() > 0 || summary.getAmbiguousCount() > 0 || summary.getUnsupportedCount() > 0);
        if (summary.getAmbiguousCount() > 0) {
            summary.setOutcome("ASSET_AMBIGUOUS");
        } else if (summary.getUnsupportedCount() > 0) {
            summary.setOutcome("UNSUPPORTED_IMAGE_TYPE");
        } else if (summary.getMissingCount() > 0) {
            summary.setOutcome("ASSET_MISSING");
        }
        return summary;
    }

    private Set<String> normalizeInventory(List<String> paths, MarkdownAssetMatchMode mode) {
        Set<String> result = new LinkedHashSet<>();
        for (String path : paths) {
            String canonical = pathResolver.normalizeCatalogPath(path);
            assertPathConstraints(canonical, mode);
            if (!result.add(canonical)) {
                throw collision(mode);
            }
        }
        return result;
    }

    private Map<String, MarkdownAssetFile> normalizeAssets(
            List<MarkdownAssetFile> assets,
            MarkdownAssetMatchMode mode) {
        Map<String, MarkdownAssetFile> result = new LinkedHashMap<>();
        for (MarkdownAssetFile asset : assets) {
            String canonical = pathResolver.normalizeCatalogPath(asset.canonicalPath());
            assertPathConstraints(canonical, mode);
            MarkdownAssetFile normalized = new MarkdownAssetFile(
                canonical,
                basename(canonical),
                asset.tempFile(),
                asset.declaredContentType(),
                asset.sizeBytes());
            if (result.putIfAbsent(canonical, normalized) != null) {
                throw collision(mode);
            }
        }
        return result;
    }

    private void assertPathConstraints(String path, MarkdownAssetMatchMode mode) {
        if (path.length() > properties.getMarkdownAssetPathMaxLength()) {
            throw new BusinessException(ErrorCode.ASSET_PATH_LENGTH_EXCEEDED);
        }
        if (mode == MarkdownAssetMatchMode.SHALLOW_BASENAME && path.contains("/")) {
            throw new BusinessException(400, "单文件补图只允许直接子级图片文件名", 400);
        }
    }

    private BusinessException collision(MarkdownAssetMatchMode mode) {
        return new BusinessException(mode == MarkdownAssetMatchMode.SHALLOW_BASENAME
            ? ErrorCode.ASSET_FILENAME_COLLISION
            : ErrorCode.ASSET_PATH_COLLISION);
    }

    private String normalizeSingleFilename(String filename) {
        String value = Normalizer.normalize(filename, Normalizer.Form.NFC);
        if (!StringUtils.hasText(value) || value.contains("/") || value.contains("\\")) {
            throw new BusinessException(400, "Markdown 文件名非法", 400);
        }
        return value;
    }

    private String readMarkdown(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .replaceFirst("^\\uFEFF", "");
        } catch (CharacterCodingException e) {
            throw new BusinessException(400, "Markdown 文件必须使用 UTF-8 编码", 400);
        } catch (IOException e) {
            throw new BusinessException(400, "Markdown 文件读取失败", 400);
        }
    }

    private Path writeString(Path directory, String prefix, String suffix, String content) {
        try {
            Path path = Files.createTempFile(directory, prefix, suffix);
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new BusinessException(500, "Markdown 规范化文件生成失败", 500);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new BusinessException(400, "文件读取失败", 400);
        }
    }

    private String sha256(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new BusinessException(500, "文件摘要计算失败", 500);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public record PreflightPlan(
        Path markdownPath,
        String markdown,
        String documentPath,
        MarkdownAssetMatchMode matchMode,
        List<Resolution> resolutions,
        Map<String, ValidatedAsset> validatedByPath,
        MarkdownAssetSummaryDTO summary
    ) {
    }

    private record StoredAsset(ValidatedAsset validated, String storedFilename, String objectKey) {
    }
}
