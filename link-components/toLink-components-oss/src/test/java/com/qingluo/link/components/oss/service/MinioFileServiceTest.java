package com.qingluo.link.components.oss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MinioFileServiceTest {

    @Test
    void Should_CreateMinioClient_When_MinioPropertiesAreConfigured() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setServiceType("minio");
        ossProperties.getMinio().setEndpoint("http://127.0.0.1:9000");
        ossProperties.getMinio().setPublicBucketName("tolink-public");
        ossProperties.getMinio().setRawBucketName("tolink-rag-raw");
        ossProperties.getMinio().setPrivateBucketName("tolink-rag-docs");
        ossProperties.getMinio().setAccessKey("root");
        ossProperties.getMinio().setSecretKey("secret123");

        assertThatCode(() -> new MinioFileService(ossProperties))
            .doesNotThrowAnyException();
    }

    @Test
    void Should_RejectMinioClient_When_AnyTwoBucketsHaveSameName() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setServiceType("minio");
        ossProperties.getMinio().setEndpoint("http://127.0.0.1:9000");
        ossProperties.getMinio().setPublicBucketName("same-bucket");
        ossProperties.getMinio().setRawBucketName("same-bucket");
        ossProperties.getMinio().setPrivateBucketName("different-bucket");
        ossProperties.getMinio().setAccessKey("root");
        ossProperties.getMinio().setSecretKey("secret123");

        assertThatThrownBy(() -> new MinioFileService(ossProperties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be unique")
            .hasMessageContaining("same-bucket");
    }

    @Test
    void Should_ReturnConfiguredPrivateBucketName_When_ResolvePrivateBucket() {
        MinioFileService service = new MinioFileService(
            minioProperties("public-bucket", "raw-bucket", "private-bucket"));

        assertThat(service.getBucketName(OssSavePlaceEnum.PRIVATE)).isEqualTo("private-bucket");
    }

    @Test
    void Should_ReturnConfiguredRawBucketName_When_ResolveRawBucket() {
        MinioFileService service = new MinioFileService(
            minioProperties("tolink-public", "tolink-rag-raw", "tolink-rag-docs"));

        assertThat(service.getBucketName(OssSavePlaceEnum.RAW)).isEqualTo("tolink-rag-raw");
    }

    @Test
    void Should_ResolveConfiguredPublicBucketName_When_ResolvePublicBucket() {
        MinioFileService service = new MinioFileService(
            minioProperties("tolink-public", "tolink-rag-raw", "tolink-rag-docs"));

        assertThat(service.getBucketName(OssSavePlaceEnum.PUBLIC)).isEqualTo("tolink-public");
    }

    @Test
    void Should_BuildPublicUrl_When_ResolvePublicUrlForPublicBucket() {
        MinioFileService service = new MinioFileService(
            minioProperties("tolink-public", "tolink-rag-raw", "tolink-rag-docs"));

        assertThat(service.resolvePublicUrl(OssSavePlaceEnum.PUBLIC, "feedback/2026/06/abc.png"))
            .isEqualTo("http://127.0.0.1:9000/tolink-public/feedback/2026/06/abc.png");
    }

    @Test
    void Should_FailFast_When_PublicBucketIsNotConfigured() {
        MinioFileService service = new MinioFileService(
            minioProperties(null, "tolink-rag-raw", "tolink-rag-docs"));

        assertThatThrownBy(() -> service.getBucketName(OssSavePlaceEnum.PUBLIC))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured")
            .hasMessageContaining("PUBLIC");
    }

    private static OssProperties minioProperties(String publicBucket, String rawBucket, String privateBucket) {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setServiceType("minio");
        ossProperties.getMinio().setEndpoint("http://127.0.0.1:9000");
        ossProperties.getMinio().setPublicBucketName(publicBucket);
        ossProperties.getMinio().setRawBucketName(rawBucket);
        ossProperties.getMinio().setPrivateBucketName(privateBucket);
        ossProperties.getMinio().setAccessKey("root");
        ossProperties.getMinio().setSecretKey("secret123");
        return ossProperties;
    }

    @Test
    void Should_DeleteExistingTargetFile_When_PreparingDownloadTarget() throws Exception {
        Path target = Files.createTempFile("tolink-minio-download-", ".tmp");
        Files.writeString(target, "old");

        Path prepared = MinioFileService.prepareDownloadTarget(target.toString());

        assertThat(prepared).isEqualTo(target.normalize());
        assertThat(Files.exists(prepared)).isFalse();
        assertThat(Files.isDirectory(prepared.getParent())).isTrue();
    }
}
