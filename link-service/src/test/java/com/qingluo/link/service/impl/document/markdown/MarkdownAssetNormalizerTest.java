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
        MockMultipartFile b = new MockMultipartFile("assets", "b.png", "image/png", "b".getBytes());
        MockMultipartFile a = new MockMultipartFile("assets", "a.png", "image/png", "a".getBytes());
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
            "http://link-api-internal"
        )).bundle();

        String normalized = Files.readString(bundle.normalizedMarkdown(), StandardCharsets.UTF_8);
        assertThat(normalized)
            .contains("http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Fa.png")
            .contains("http://link-api-internal/api/v1/internal/files/101/assets?path=icons%2Fb.png")
            .contains("普通文本里的 ./images/a.png 不应该被替换。")
            .contains("![Missing](images/missing.png)");
        assertThat(bundle.manifest().missingAssets()).containsExactly("images/missing.png");
        JSONObject manifest = JSON.parseObject(Files.readString(bundle.manifestFile(), StandardCharsets.UTF_8));
        assertThat(manifest.getJSONArray("missingAssets").toJavaList(String.class))
            .containsExactly("images/missing.png");
        assertThat(manifest.getJSONObject("rewrittenAssets").getString("images/a.png"))
            .isEqualTo("http://link-api-internal/api/v1/internal/files/101/assets?path=images%2Fa.png");
        assertThat(bundle.assets())
            .extracting(MarkdownUploadBundle.AssetFile::objectKey)
            .containsExactly(
                "user-401/dataset-201/file-101/assets/icons/b.png",
                "user-401/dataset-201/file-101/assets/images/a.png");
    }

    @Test
    void rejectsSameRelativePathWithDifferentContent() {
        MockMultipartFile first = new MockMultipartFile("assets", "a.png", "image/png", "a".getBytes());
        MockMultipartFile second = new MockMultipartFile("assets", "a.png", "image/png", "different".getBytes());

        assertThatThrownBy(() -> normalizer.materializedAssetPaths(
            List.of(first, second),
            List.of("images/a.png", "images/a.png"),
            file -> Path.of("/tmp/" + file.getOriginalFilename())))
            .isInstanceOf(BusinessException.class)
            .hasMessage("配套图片路径冲突");
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
}
