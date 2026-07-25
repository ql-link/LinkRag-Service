package com.qingluo.link.service.impl.document.markdown;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.response.MarkdownAssetSummaryDTO;
import com.qingluo.link.model.enums.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MarkdownAssetManifestStore {

    private final IOssService ossService;

    public MarkdownAssetManifestStore(IOssService ossService) {
        this.ossService = ossService;
    }

    public Path materialize(Path directory, MarkdownAssetManifest manifest) {
        try {
            Path target = Files.createTempFile(directory, "manifest-", ".json");
            Files.writeString(target, JSON.toJSONString(manifest), StandardCharsets.UTF_8);
            return target;
        } catch (IOException e) {
            throw new BusinessException(500, "Markdown 图片清单生成失败", 500);
        }
    }

    public MarkdownAssetManifest readRequired(DocumentOriginalFile file) {
        if (!MarkdownAssetObjectKeys.isV1NormalizedSource(file.getObjectKey())) {
            return null;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-markdown-manifest-", ".json");
            String manifestKey = MarkdownAssetObjectKeys.manifestKeyFromNormalized(file.getObjectKey());
            if (!ossService.downloadFile(OssSavePlaceEnum.RAW, manifestKey, temp.toString())) {
                throw unavailable();
            }
            MarkdownAssetManifest manifest = JSON.parseObject(
                Files.readString(temp, StandardCharsets.UTF_8), MarkdownAssetManifest.class);
            validate(file, manifest);
            return manifest;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Markdown asset manifest read failed, fileId={}", file.getId(), e);
            throw unavailable();
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理不覆盖原始 manifest 读取结果。
                }
            }
        }
    }

    public MarkdownAssetSummaryDTO readSummary(DocumentOriginalFile file) {
        MarkdownAssetManifest manifest = readRequired(file);
        return manifest == null ? null : manifest.getSummary();
    }

    private void validate(DocumentOriginalFile file, MarkdownAssetManifest manifest) {
        if (manifest == null || manifest.getVersion() != 1 || manifest.getSummary() == null
            || !file.getId().equals(manifest.getFileId())
            || !file.getUserId().equals(manifest.getUserId())
            || !file.getDatasetId().equals(manifest.getDatasetId())
            || manifest.getSource() == null
            || !file.getObjectKey().equals(manifest.getSource().getNormalizedObjectKey())) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(
            ErrorCode.ASSET_MANIFEST_UNAVAILABLE,
            ErrorCode.ASSET_MANIFEST_UNAVAILABLE.getMessage(),
            java.util.Map.of("errorKind", "ASSET_MANIFEST_UNAVAILABLE"));
    }
}
