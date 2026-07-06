package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

class MarkdownAssetPathNormalizerTest {

    @Test
    void normalizesCommonRelativePaths() {
        assertThat(MarkdownAssetPathNormalizer.normalize("./images/a%20b.png"))
            .isEqualTo("images/a b.png");
        assertThat(MarkdownAssetPathNormalizer.normalize("images\\a.png"))
            .isEqualTo("images/a.png");
        assertThat(MarkdownAssetPathNormalizer.normalize("images//a.png?x=1#top"))
            .isEqualTo("images/a.png");
    }

    @Test
    void rejectsUnsafePaths() {
        assertThatThrownBy(() -> MarkdownAssetPathNormalizer.normalize("../secret.png"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MarkdownAssetPathNormalizer.normalize("/etc/passwd"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MarkdownAssetPathNormalizer.normalize("C:\\secret.png"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void filtersRemoteAndInlineImages() {
        assertThat(MarkdownAssetPathNormalizer.isLocalAssetPath("https://example.com/a.png")).isFalse();
        assertThat(MarkdownAssetPathNormalizer.isLocalAssetPath("data:image/png;base64,aaa")).isFalse();
        assertThat(MarkdownAssetPathNormalizer.isLocalAssetPath("./images/a.png")).isTrue();
    }
}
