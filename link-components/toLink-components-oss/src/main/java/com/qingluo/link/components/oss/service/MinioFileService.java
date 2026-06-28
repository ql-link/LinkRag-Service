package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import io.minio.BucketExistsArgs;
import io.minio.DownloadObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO-backed OSS provider.
 */
@Service
@ConditionalOnProperty(name = OssProperties.SERVICE_TYPE_PROPERTY, havingValue = "minio")
public class MinioFileService implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileService.class);

    private final OssProperties ossProperties;
    private final MinioClient minioClient;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    public MinioFileService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        OssProperties.Minio minio = ossProperties.getMinio();
        validateBucketNames(minio);
        this.minioClient = MinioClient.builder()
            .endpoint(minio.getEndpoint())
            .credentials(minio.getAccessKey(), minio.getSecretKey())
            .build();
    }

    @Override
    public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, MultipartFile multipartFile, String saveDirAndFileName) {
        String bucket = resolveBucketName(ossSavePlaceEnum);
        try (InputStream inputStream = multipartFile.getInputStream()) {
            ensureBucket(bucket);
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(saveDirAndFileName)
                    .stream(inputStream, multipartFile.getSize(), -1)
                    .contentType(multipartFile.getContentType())
                    .build());

            if (returnsObjectKeyOnly(ossSavePlaceEnum)) {
                return saveDirAndFileName;
            }
            return buildPublicUrl(bucket, saveDirAndFileName);
        } catch (Exception e) {
            log.error("Upload MinIO file failed, place={}, objectKey={}", ossSavePlaceEnum, saveDirAndFileName, e);
            return null;
        }
    }

    @Override
    public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, File localFile, String contentType, String saveDirAndFileName) {
        String bucket = resolveBucketName(ossSavePlaceEnum);
        try (InputStream inputStream = Files.newInputStream(localFile.toPath())) {
            ensureBucket(bucket);
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(saveDirAndFileName)
                    .stream(inputStream, localFile.length(), -1)
                    .contentType(contentType)
                    .build());

            if (returnsObjectKeyOnly(ossSavePlaceEnum)) {
                return saveDirAndFileName;
            }
            return buildPublicUrl(bucket, saveDirAndFileName);
        } catch (Exception e) {
            log.error("Upload MinIO file (from local file) failed, place={}, objectKey={}",
                ossSavePlaceEnum, saveDirAndFileName, e);
            return null;
        }
    }

    @Override
    public boolean downloadFile(OssSavePlaceEnum ossSavePlaceEnum, String source, String target) {
        String bucket = resolveBucketName(ossSavePlaceEnum);
        try {
            Path targetPath = prepareDownloadTarget(target);
            minioClient.downloadObject(
                DownloadObjectArgs.builder()
                    .bucket(bucket)
                    .object(source)
                    .filename(targetPath.toString())
                    .build());
            return true;
        } catch (Exception e) {
            log.error("Download MinIO file failed, place={}, source={}, target={}",
                ossSavePlaceEnum, source, target, e);
            return false;
        }
    }

    static Path prepareDownloadTarget(String target) throws java.io.IOException {
        Path targetPath = Path.of(target).normalize();
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(targetPath);
        return targetPath;
    }

    @Override
    public boolean deleteFile(OssSavePlaceEnum ossSavePlaceEnum, String objectKey) {
        String bucket = resolveBucketName(ossSavePlaceEnum);
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("Delete MinIO file failed, place={}, objectKey={}", ossSavePlaceEnum, objectKey, e);
            return false;
        }
    }

    @Override
    public String getBucketName(OssSavePlaceEnum ossSavePlaceEnum) {
        return resolveBucketName(ossSavePlaceEnum);
    }

    @Override
    public String resolvePublicUrl(OssSavePlaceEnum ossSavePlaceEnum, String objectKey) {
        return buildPublicUrl(resolveBucketName(ossSavePlaceEnum), objectKey);
    }

    private String buildPublicUrl(String bucket, String objectKey) {
        return normalizeEndpoint() + "/" + bucket + "/" + objectKey;
    }

    private void ensureBucket(String bucket) throws Exception {
        if (ensuredBuckets.contains(bucket)) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        ensuredBuckets.add(bucket);
    }

    private String resolveBucketName(OssSavePlaceEnum place) {
        OssProperties.Minio minio = ossProperties.getMinio();
        String bucket = switch (place) {
            case PUBLIC -> minio.getPublicBucketName();
            case RAW -> minio.getRawBucketName();
            case PRIVATE -> minio.getPrivateBucketName();
        };
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("MinIO bucket is not configured for " + place);
        }
        return bucket;
    }

    private static boolean returnsObjectKeyOnly(OssSavePlaceEnum place) {
        return place == OssSavePlaceEnum.RAW || place == OssSavePlaceEnum.PRIVATE;
    }

    private void validateBucketNames(OssProperties.Minio minio) {
        String publicBucket = minio.getPublicBucketName();
        String rawBucket = minio.getRawBucketName();
        String privateBucket = minio.getPrivateBucketName();
        Set<String> configured = new java.util.HashSet<>();
        for (String b : new String[]{publicBucket, rawBucket, privateBucket}) {
            if (StringUtils.hasText(b) && !configured.add(b)) {
                throw new IllegalStateException("MinIO bucket names must be unique; duplicate: " + b);
            }
        }
    }

    private String normalizeEndpoint() {
        String endpoint = ossProperties.getMinio().getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("MinIO endpoint is blank");
        }
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }
}
