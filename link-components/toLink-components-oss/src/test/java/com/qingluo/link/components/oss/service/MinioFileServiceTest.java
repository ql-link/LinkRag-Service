package com.qingluo.link.components.oss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import org.junit.jupiter.api.Test;

class MinioFileServiceTest {

    @Test
    void Should_CreateMinioClient_When_MinioPropertiesAreConfigured() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setServiceType("minio");
        ossProperties.getMinio().setEndpoint("http://127.0.0.1:9000");
        ossProperties.getMinio().setPublicBucketName("rag-raw");
        ossProperties.getMinio().setPrivateBucketName("rag-raw");
        ossProperties.getMinio().setAccessKey("root");
        ossProperties.getMinio().setSecretKey("secret123");

        assertThatCode(() -> new MinioFileService(ossProperties))
            .doesNotThrowAnyException();
    }

    @Test
    void Should_ReturnConfiguredPrivateBucketName_When_ResolvePrivateBucket() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setServiceType("minio");
        ossProperties.getMinio().setEndpoint("http://127.0.0.1:9000");
        ossProperties.getMinio().setPublicBucketName("public-bucket");
        ossProperties.getMinio().setPrivateBucketName("private-bucket");
        ossProperties.getMinio().setAccessKey("root");
        ossProperties.getMinio().setSecretKey("secret123");

        MinioFileService service = new MinioFileService(ossProperties);

        assertThat(service.getBucketName(OssSavePlaceEnum.PRIVATE)).isEqualTo("private-bucket");
    }
}
