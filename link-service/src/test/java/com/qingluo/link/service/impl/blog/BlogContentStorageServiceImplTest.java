package com.qingluo.link.service.impl.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.BlogAssetType;
import com.qingluo.link.service.BlogContentStorageService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class BlogContentStorageServiceImplTest {

    private RecordingOssService ossService;
    private BlogContentStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        ossService = new RecordingOssService();
        storageService = new BlogContentStorageServiceImpl(ossService);
    }

    @Test
    void Should_UploadMarkdownWithUuidKey_When_FileIsValid() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "post.md", "application/octet-stream", "# 标题\n正文".getBytes());

        String objectKey = storageService.uploadMarkdown(12L, file);

        assertThat(objectKey).matches("blog/12/content/[a-f0-9]{32}\\.md");
        assertThat(objectKey).isEqualTo(ossService.lastObjectKey);
        assertThat(ossService.lastPlace).isEqualTo(OssSavePlaceEnum.PRIVATE);
    }

    @Test
    void Should_RejectNonMarkdownAndInvalidUtf8_When_UploadingMarkdown() {
        MockMultipartFile textFile = new MockMultipartFile("file", "post.txt", "text/plain", "x".getBytes());
        MockMultipartFile invalidUtf8 = new MockMultipartFile(
            "file", "post.md", "text/markdown", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> storageService.uploadMarkdown(12L, textFile))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持");
        assertThatThrownBy(() -> storageService.uploadMarkdown(12L, invalidUtf8))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("UTF-8");
    }

    @Test
    void Should_NotApplyBusinessSizeLimit_When_UploadingMarkdown() {
        byte[] content = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(content, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile("file", "large.md", "text/markdown", content);

        String objectKey = storageService.uploadMarkdown(12L, file);

        assertThat(objectKey).matches("blog/12/content/[a-f0-9]{32}\\.md");
    }

    @Test
    void Should_UploadAllowedImageAndRejectMismatchedMime() {
        MockMultipartFile valid = new MockMultipartFile("file", "cover.webp", "image/webp", "image".getBytes());
        MockMultipartFile invalid = new MockMultipartFile("file", "cover.webp", "image/svg+xml", "image".getBytes());

        BlogContentStorageService.StoredObject stored =
            storageService.uploadImage(12L, BlogAssetType.COVER, valid);

        assertThat(stored.objectKey()).matches("blog/12/cover/[a-f0-9]{32}\\.webp");
        assertThat(stored.publicUrl()).isEqualTo("/public/" + stored.objectKey());
        assertThatThrownBy(() -> storageService.uploadImage(12L, BlogAssetType.COVER, invalid))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("MIME");
    }

    @Test
    void Should_NotApplyBusinessSizeLimit_When_UploadingImage() {
        byte[] content = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", content);

        BlogContentStorageService.StoredObject stored =
            storageService.uploadImage(12L, BlogAssetType.CONTENT_IMAGE, file);

        assertThat(stored.objectKey()).matches("blog/12/content/[a-f0-9]{32}\\.png");
    }

    private static class RecordingOssService implements IOssService {

        private OssSavePlaceEnum lastPlace;
        private String lastObjectKey;

        @Override
        public String upload2PreviewUrl(OssSavePlaceEnum place, MultipartFile file, String objectKey) {
            lastPlace = place;
            lastObjectKey = objectKey;
            return place == OssSavePlaceEnum.PUBLIC ? "/public/" + objectKey : objectKey;
        }

        @Override
        public String upload2PreviewUrl(OssSavePlaceEnum place, File file, String contentType, String objectKey) {
            lastPlace = place;
            lastObjectKey = objectKey;
            return place == OssSavePlaceEnum.PUBLIC ? "/public/" + objectKey : objectKey;
        }

        @Override
        public boolean downloadFile(OssSavePlaceEnum place, String source, String target) {
            try {
                Files.writeString(Path.of(target), "# 正文");
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean deleteFile(OssSavePlaceEnum place, String objectKey) {
            return true;
        }

        @Override
        public String getBucketName(OssSavePlaceEnum place) {
            return place.name().toLowerCase();
        }
    }
}
