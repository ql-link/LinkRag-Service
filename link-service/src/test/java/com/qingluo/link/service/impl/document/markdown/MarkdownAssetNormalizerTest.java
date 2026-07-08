package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class MarkdownAssetNormalizerTest {

    private final MarkdownAssetNormalizer normalizer = new MarkdownAssetNormalizer();

    @Test
    void rewritesUploadedAssetsByPathAndKeepsMissingReferences() throws Exception {
        Path markdown = Files.createTempFile("intro-", ".md");
        Files.writeString(markdown, """
            ![A](./images/a.png)
            普通文本里的 ./images/a.png 不应该被替换。
            ![B](icons/b.png)
            ![Missing](images/missing.png)
            """, StandardCharsets.UTF_8);
        MockMultipartFile b = new MockMultipartFile("assets", "b.png", "image/png", pngBytes(2));
        MockMultipartFile a = new MockMultipartFile("assets", "a.png", "image/png", pngBytes(1));
        List<MultipartFile> assets = List.of(b, a);
        List<String> paths = List.of("icons/b.png", "images/a.png");
        Map<String, Path> assetTemps = normalizer.materializedAssetPaths(
            assets, paths, this::writeTemp);

        MarkdownUploadBundle bundle = normalizer.prepare(new MarkdownAssetNormalizer.PrepareCommand(
            401L,
            201L,
            101L,
            "intro.md",
            markdown,
            assets,
            paths,
            assetTemps,
            "http://link-api-internal",
            "internal-token"
        )).bundle();

        String normalized = Files.readString(bundle.normalizedMarkdown(), StandardCharsets.UTF_8);
        assertThat(normalized)
            .contains("http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Fa.png&token=internal-token")
            .contains("http://link-api-internal/api/v1/internal/files/101/assets?path=icons%2Fb.png&token=internal-token")
            .contains("普通文本里的 ./images/a.png 不应该被替换。")
            .contains("![Missing](images/missing.png)");
        assertThat(bundle.manifest().missingAssets()).containsExactly("images/missing.png");
        JSONObject manifest = JSON.parseObject(Files.readString(bundle.manifestFile(), StandardCharsets.UTF_8));
        assertThat(manifest.getJSONArray("missingAssets").toJavaList(String.class))
            .containsExactly("images/missing.png");
        assertThat(manifest.getJSONObject("rewrittenAssets").getString("images/a.png"))
            .isEqualTo("http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Fa.png&token=internal-token");
        assertThat(bundle.assets())
            .extracting(MarkdownUploadBundle.AssetFile::objectKey)
            .containsExactly(
                "user-401/dataset-201/file-101/assets/icons/b.png",
                "user-401/dataset-201/file-101/assets/images/a.png");
    }

    @Test
    void rewritesAngleHtmlAndReferenceStyleImages() throws Exception {
        Path markdown = Files.createTempFile("rich-", ".md");
        Files.writeString(markdown, """
            ![Space](<images/a b.png>)
            <img alt="html" src='html/c.png'>
            ![Logo][logo]

            [logo]: icons/logo.png "Logo"
            """, StandardCharsets.UTF_8);
        List<MultipartFile> assets = List.of(
            new MockMultipartFile("assets", "a b.png", "image/png", pngBytes(1)),
            new MockMultipartFile("assets", "c.png", "image/png", pngBytes(2)),
            new MockMultipartFile("assets", "logo.png", "image/png", pngBytes(3)));
        List<String> paths = List.of("images/a b.png", "html/c.png", "icons/logo.png");
        Map<String, Path> assetTemps = normalizer.materializedAssetPaths(assets, paths, this::writeTemp);

        MarkdownUploadBundle bundle = normalizer.prepare(new MarkdownAssetNormalizer.PrepareCommand(
            401L,
            201L,
            101L,
            "rich.md",
            markdown,
            assets,
            paths,
            assetTemps,
            "http://link-api-internal",
            null
        )).bundle();

        String normalized = Files.readString(bundle.normalizedMarkdown(), StandardCharsets.UTF_8);
        assertThat(normalized)
            .contains("![Space](<http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Fa%20b.png>)")
            .contains("<img alt=\"html\" src='http://link-api-internal/api/v1/internal/files/101/assets?path=html%2Fc.png'>")
            .contains("[logo]: http://link-api-internal/api/v1/internal/files/101/assets?path=icons%2Flogo.png \"Logo\"");
        assertThat(bundle.manifest().missingAssets()).isEmpty();
    }

    @Test
    void ignoresImageLikeSyntaxInsideCode() throws Exception {
        Path markdown = Files.createTempFile("code-", ".md");
        Files.writeString(markdown, """
            `![Inline](images/inline.png)`

            ```markdown
            ![Code](images/code.png)
            <img src="images/html.png">
            ```

            ![Real](images/real.png)
            """, StandardCharsets.UTF_8);
        List<MultipartFile> assets = List.of(
            new MockMultipartFile("assets", "real.png", "image/png", pngBytes(1)));
        List<String> paths = List.of("images/real.png");
        Map<String, Path> assetTemps = normalizer.materializedAssetPaths(assets, paths, this::writeTemp);

        MarkdownUploadBundle bundle = normalizer.prepare(new MarkdownAssetNormalizer.PrepareCommand(
            401L,
            201L,
            101L,
            "code.md",
            markdown,
            assets,
            paths,
            assetTemps,
            "http://link-api-internal",
            null
        )).bundle();

        String normalized = Files.readString(bundle.normalizedMarkdown(), StandardCharsets.UTF_8);
        assertThat(normalized)
            .contains("`![Inline](images/inline.png)`")
            .contains("![Code](images/code.png)")
            .contains("<img src=\"images/html.png\">")
            .contains("![Real](http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Freal.png)");
        assertThat(bundle.manifest().missingAssets()).isEmpty();
    }

    @Test
    void rejectsSameRelativePathWithDifferentContent() {
        MockMultipartFile first = new MockMultipartFile("assets", "a.png", "image/png", pngBytes(1));
        MockMultipartFile second = new MockMultipartFile("assets", "a.png", "image/png", pngBytes(2));

        assertThatThrownBy(() -> normalizer.materializedAssetPaths(
            List.of(first, second),
            List.of("images/a.png", "images/a.png"),
            file -> Path.of("/tmp/" + file.getOriginalFilename())))
            .isInstanceOf(BusinessException.class)
            .hasMessage("配套图片路径冲突");
    }

    @Test
    void rejectsNonImageAssetContent() {
        MockMultipartFile file = new MockMultipartFile("assets", "a.png", "image/png", "not-png".getBytes());

        assertThatThrownBy(() -> normalizer.materializedAssetPaths(
            List.of(file),
            List.of("images/a.png"),
            this::writeTemp))
            .isInstanceOf(BusinessException.class)
            .hasMessage("配套图片内容不是有效图片");
    }

    private Path writeTemp(String filename, byte[] content) {
        try {
            Path path = Files.createTempFile("asset-" + filename, ".tmp");
            Files.write(path, content);
            return path;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Path writeTemp(MultipartFile file) {
        try {
            return writeTemp(file.getOriginalFilename(), file.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] pngBytes(int marker) {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, (byte) marker
        };
    }
}
