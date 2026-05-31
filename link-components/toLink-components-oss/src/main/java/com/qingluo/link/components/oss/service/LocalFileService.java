package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.enums.OssServiceTypeEnum;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local disk OSS provider.
 */
@Service
@ConditionalOnProperty(
        name = OssProperties.SERVICE_TYPE_PROPERTY,
        havingValue = "local",
        matchIfMissing = true)
public class LocalFileService implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileService.class);
    private static final String LOCAL_PUBLIC_BUCKET = "local-public";
    private static final String LOCAL_PRIVATE_BUCKET = "local-private";

    private final OssProperties ossProperties;

    public LocalFileService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @Override
    public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, MultipartFile multipartFile, String saveDirAndFileName) {
        try {
            Path target = resolveStoragePath(ossSavePlaceEnum, saveDirAndFileName);
            Files.createDirectories(target.getParent());
            multipartFile.transferTo(target);

            if (OssSavePlaceEnum.PRIVATE == ossSavePlaceEnum) {
                return saveDirAndFileName;
            }
            return normalizePublicBaseUrl() + "/" + saveDirAndFileName;
        } catch (Exception e) {
            log.error("Upload local OSS file failed, place={}, objectKey={}", ossSavePlaceEnum, saveDirAndFileName, e);
            return null;
        }
    }

    @Override
    public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, File localFile, String contentType, String saveDirAndFileName) {
        try {
            Path target = resolveStoragePath(ossSavePlaceEnum, saveDirAndFileName);
            Files.createDirectories(target.getParent());
            // contentType 对本地存储无意义（仅 MinIO 用），此处按对象键落盘即可。
            Files.copy(localFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            if (OssSavePlaceEnum.PRIVATE == ossSavePlaceEnum) {
                return saveDirAndFileName;
            }
            return normalizePublicBaseUrl() + "/" + saveDirAndFileName;
        } catch (Exception e) {
            log.error("Upload local OSS file (from local file) failed, place={}, objectKey={}",
                    ossSavePlaceEnum, saveDirAndFileName, e);
            return null;
        }
    }

    @Override
    public boolean downloadFile(OssSavePlaceEnum ossSavePlaceEnum, String source, String target) {
        try {
            Path sourcePath = resolveStoragePath(ossSavePlaceEnum, source);
            Path targetPath = Path.of(target).normalize();
            Files.createDirectories(targetPath.getParent());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Download local OSS file failed, place={}, source={}, target={}",
                    ossSavePlaceEnum, source, target, e);
            return false;
        }
    }

    @Override
    public boolean deleteFile(OssSavePlaceEnum ossSavePlaceEnum, String objectKey) {
        try {
            Path target = resolveStoragePath(ossSavePlaceEnum, objectKey);
            Files.deleteIfExists(target);
            Files.deleteIfExists(Path.of(target.toString() + ".notexists"));
            return true;
        } catch (IOException e) {
            log.error("Delete local OSS file failed, place={}, objectKey={}", ossSavePlaceEnum, objectKey, e);
            return false;
        }
    }

    @Override
    public String getBucketName(OssSavePlaceEnum ossSavePlaceEnum) {
        return OssSavePlaceEnum.PUBLIC == ossSavePlaceEnum ? LOCAL_PUBLIC_BUCKET : LOCAL_PRIVATE_BUCKET;
    }

    private Path resolveStoragePath(OssSavePlaceEnum place, String objectKey) throws IOException {
        if (!StringUtils.hasText(objectKey)) {
            throw new IOException("Object key is blank");
        }
        String basePath = OssSavePlaceEnum.PUBLIC == place
                ? ossProperties.getFilePublicPath()
                : ossProperties.getFilePrivatePath();
        Path base = Path.of(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Illegal object key: " + objectKey);
        }
        return target;
    }

    private String normalizePublicBaseUrl() {
        String baseUrl = ossProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Public base URL is not configured");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
