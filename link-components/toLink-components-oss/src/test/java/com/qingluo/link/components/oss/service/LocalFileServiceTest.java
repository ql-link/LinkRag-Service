package com.qingluo.link.components.oss.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.components.oss.config.OssProperties;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileServiceTest {

    @TempDir
    Path tempDir;

    private OssProperties ossProperties;
    private LocalFileService localFileService;

    @BeforeEach
    void setUp() {
        ossProperties = new OssProperties();
        ossProperties.setFileRootPath(tempDir.toString());
        ossProperties.setPublicBaseUrl("/api/v1/oss-files/public");
        localFileService = new LocalFileService(ossProperties);
    }

    @Test
    void Should_ReturnPreviewUrlAndWritePublicFile_When_UploadPublicFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "hello".getBytes());

        String result = localFileService.upload2PreviewUrl(OssSavePlaceEnum.PUBLIC, file, "avatar/test.png");

        assertThat(result).isEqualTo("/api/v1/oss-files/public/avatar/test.png");
        assertThat(Files.readString(Path.of(ossProperties.getFilePublicPath(), "avatar/test.png"))).isEqualTo("hello");
    }

    @Test
    void Should_ReturnObjectKeyAndWritePrivateFile_When_UploadPrivateFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cert.pem", "text/plain", "secret".getBytes());

        String result = localFileService.upload2PreviewUrl(OssSavePlaceEnum.PRIVATE, file, "cert/test.pem");

        assertThat(result).isEqualTo("cert/test.pem");
        assertThat(Files.readString(Path.of(ossProperties.getFilePrivatePath(), "cert/test.pem"))).isEqualTo("secret");
    }

    @Test
    void Should_ReturnObjectKeyAndWriteRawFile_When_UploadRawFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", "raw".getBytes());

        String result = localFileService.upload2PreviewUrl(OssSavePlaceEnum.RAW, file, "document/source.pdf");

        assertThat(result).isEqualTo("document/source.pdf");
        assertThat(Files.readString(tempDir.resolve("raw/document/source.pdf"))).isEqualTo("raw");
    }

    @Test
    void Should_CopyStoredFile_When_DownloadFile() throws Exception {
        Path source = Path.of(ossProperties.getFilePrivatePath(), "cert/test.pem");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "secret");
        Path target = tempDir.resolve("cache/cert.pem");

        boolean result = localFileService.downloadFile(OssSavePlaceEnum.PRIVATE, "cert/test.pem", target.toString());

        assertThat(result).isTrue();
        assertThat(Files.readString(target)).isEqualTo("secret");
    }

    @Test
    void Should_ReturnLogicalBucketName_When_ResolvePrivateBucket() {
        assertThat(localFileService.getBucketName(OssSavePlaceEnum.RAW)).isEqualTo("local-raw");
        assertThat(localFileService.getBucketName(OssSavePlaceEnum.PRIVATE)).isEqualTo("local-private");
    }

    @Test
    void Should_DeleteStoredPrivateFile_When_DeleteFile() throws Exception {
        Path source = Path.of(ossProperties.getFilePrivatePath(), "cert/test.pem");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "secret");

        boolean result = localFileService.deleteFile(OssSavePlaceEnum.PRIVATE, "cert/test.pem");

        assertThat(result).isTrue();
        assertThat(Files.exists(source)).isFalse();
    }

    @Test
    void Should_Fail_Public_Upload_When_Public_Base_Url_Is_Not_Configured() {
        ossProperties.setPublicBaseUrl(null);
        localFileService = new LocalFileService(ossProperties);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "hello".getBytes());

        String result = localFileService.upload2PreviewUrl(OssSavePlaceEnum.PUBLIC, file, "avatar/test.png");

        assertThat(result).isNull();
    }
}
