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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

        BlogContentStorageService.ProcessedMarkdown processed =
            storageService.importMarkdown(12L, file, Set.of());

        assertThat(processed.objectKey()).matches("blog/12/content/[a-f0-9]{32}\\.md");
        assertThat(processed.objectKey()).isEqualTo(ossService.lastObjectKey);
        assertThat(processed.contentMarkdown()).isEqualTo("# 标题\n正文");
        assertThat(processed.images()).isEmpty();
        assertThat(ossService.lastPlace).isEqualTo(OssSavePlaceEnum.BLOG);
    }

    @Test
    void Should_RewriteDataUriImagesToPublicOssUrls_When_UploadingMarkdown() {
        String imageData = Base64.getEncoder().encodeToString("image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
            "file", "post.md", "text/markdown",
            ("# 标题\n![图](data:image/png;base64," + imageData + ")\n正文").getBytes(StandardCharsets.UTF_8));

        BlogContentStorageService.ProcessedMarkdown processed =
            storageService.importMarkdown(12L, file, Set.of());

        assertThat(processed.objectKey()).matches("blog/12/content/[a-f0-9]{32}\\.md");
        assertThat(ossService.publicObjectKeys).hasSize(1);
        assertThat(ossService.publicObjectKeys.get(0)).matches("blog/12/images/[a-f0-9]{32}\\.png");
        assertThat(ossService.privateMarkdown).contains("![图](/public/" + ossService.publicObjectKeys.get(0) + ")");
        assertThat(ossService.privateMarkdown).doesNotContain("data:image");
        assertThat(processed.images()).hasSize(1);
        assertThat(processed.images().get(0).publicUrl()).isEqualTo("/public/" + ossService.publicObjectKeys.get(0));
    }

    @Test
    void Should_RejectNonMarkdownAndInvalidUtf8_When_UploadingMarkdown() {
        MockMultipartFile textFile = new MockMultipartFile("file", "post.txt", "text/plain", "x".getBytes());
        MockMultipartFile invalidUtf8 = new MockMultipartFile(
            "file", "post.md", "text/markdown", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> storageService.importMarkdown(12L, textFile, Set.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持");
        assertThatThrownBy(() -> storageService.importMarkdown(12L, invalidUtf8, Set.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("UTF-8");
    }

    @Test
    void Should_NotApplyBusinessSizeLimit_When_UploadingMarkdown() {
        byte[] content = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(content, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile("file", "large.md", "text/markdown", content);

        BlogContentStorageService.ProcessedMarkdown processed =
            storageService.importMarkdown(12L, file, Set.of());

        assertThat(processed.objectKey()).matches("blog/12/content/[a-f0-9]{32}\\.md");
    }

    @Test
    void Should_PreserveRestrictedRemoteImageUrl_When_SavingMarkdown() {
        BlogContentStorageService.ProcessedMarkdown processed = storageService.saveMarkdown(
            12L,
            "# 标题\n![图](http://127.0.0.1/a.png)",
            Set.of());

        assertThat(processed.contentMarkdown()).contains("![图](http://127.0.0.1/a.png)");
        assertThat(processed.images()).isEmpty();
        assertThat(ossService.publicObjectKeys).isEmpty();
    }

    @Test
    void Should_RejectRelativeImagePath_When_SavingMarkdown() {
        assertThatThrownBy(() -> storageService.saveMarkdown(12L, "# 标题\n![图](./images/a.png)", Set.of()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持");
    }

    @Test
    void Should_SkipKnownPublicUrl_When_SavingMarkdown() {
        String publicUrl = "/public/blog/12/content/a.png";

        BlogContentStorageService.ProcessedMarkdown processed = storageService.saveMarkdown(
            12L,
            "# 标题\n![图](" + publicUrl + ")",
            Set.of(publicUrl));

        assertThat(processed.contentMarkdown()).contains(publicUrl);
        assertThat(processed.images()).isEmpty();
        assertThat(ossService.publicObjectKeys).isEmpty();
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

        assertThat(stored.objectKey()).matches("blog/12/images/[a-f0-9]{32}\\.png");
    }

    private static class RecordingOssService implements IOssService {

        private OssSavePlaceEnum lastPlace;
        private String lastObjectKey;
        private String privateMarkdown;
        private final List<String> publicObjectKeys = new ArrayList<>();

        @Override
        public String upload2PreviewUrl(OssSavePlaceEnum place, MultipartFile file, String objectKey) {
            lastPlace = place;
            lastObjectKey = objectKey;
            publicObjectKeys.add(objectKey);
            return "/public/" + objectKey;
        }

        @Override
        public String upload2PreviewUrl(OssSavePlaceEnum place, File file, String contentType, String objectKey) {
            lastPlace = place;
            lastObjectKey = objectKey;
            if ("text/markdown".equals(contentType)) {
                try {
                    privateMarkdown = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                publicObjectKeys.add(objectKey);
            }
            return "/public/" + objectKey;
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
