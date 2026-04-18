package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.enums.OssServiceTypeEnum;
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
        } catch (IOException e) {
            log.error("Upload local OSS file failed, place={}, objectKey={}", ossSavePlaceEnum, saveDirAndFileName, e);
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
            return "/api/v1/oss-files/public";
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
