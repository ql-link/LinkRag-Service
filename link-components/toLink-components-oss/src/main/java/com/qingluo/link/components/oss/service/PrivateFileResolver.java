package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.enums.OssServiceTypeEnum;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves private object keys to local files for SDKs that require file paths.
 */
@Component
public class PrivateFileResolver {

    private final OssProperties ossProperties;
    private final IOssService ossService;

    public PrivateFileResolver(OssProperties ossProperties, IOssService ossService) {
        this.ossProperties = ossProperties;
        this.ossService = ossService;
    }

    public File getPrivateFile(String objectKey) {
        return getPrivateFile(OssSavePlaceEnum.PRIVATE, objectKey);
    }

    public File getPrivateFile(OssSavePlaceEnum place, String objectKey) {
        Path target = resolvePrivatePath(objectKey);
        if (Files.exists(target) || OssServiceTypeEnum.LOCAL.getServiceName().equals(ossProperties.getServiceType())) {
            return target.toFile();
        }

        Path marker = Path.of(target.toString() + ".notexists");
        if (Files.exists(marker)) {
            return target.toFile();
        }

        try {
            Files.createDirectories(target.getParent());
            if (ossService.downloadFile(place, objectKey, target.toString())) {
                return target.toFile();
            }
            Files.createFile(marker);
        } catch (IOException ignored) {
        }
        return target.toFile();
    }

    public void evictPrivateFile(String objectKey) {
        Path target = resolvePrivatePath(objectKey);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(Path.of(target.toString() + ".notexists"));
        } catch (IOException ignored) {
        }
    }

    private Path resolvePrivatePath(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("Object key is blank");
        }
        Path base = Path.of(ossProperties.getFilePrivatePath()).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Illegal object key: " + objectKey);
        }
        return target;
    }
}
