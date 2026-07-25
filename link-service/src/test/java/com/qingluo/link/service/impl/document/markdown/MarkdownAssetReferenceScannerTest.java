package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownAssetReferenceScannerTest {

    private final MarkdownAssetReferenceScanner scanner = new MarkdownAssetReferenceScanner();

    @Test
    void scansAndRewritesAllSupportedSyntaxes() {
        String markdown = """
            ![inline](images/a.png)
            ![reference][id]
            [id]: images/a.png "title"
            <img
              src='images/a.png' alt='html'>
            ![[images/a.png]]
            ![[images/a.png\\|架构图]]
            """;

        List<MarkdownAssetReference> references = scanner.scan(markdown);

        assertThat(references).hasSize(5);
        assertThat(references).extracting(MarkdownAssetReference::syntax)
            .containsExactly(
                MarkdownAssetReference.Syntax.MARKDOWN,
                MarkdownAssetReference.Syntax.MARKDOWN_REFERENCE,
                MarkdownAssetReference.Syntax.HTML,
                MarkdownAssetReference.Syntax.OBSIDIAN,
                MarkdownAssetReference.Syntax.OBSIDIAN);
        String uri = "tolink-raw://raw/markdown-assets/v1/user-1/dataset-2/file-3/images/image-abc.png";
        String rewritten = scanner.rewrite(markdown, references.stream()
            .map(reference -> new MarkdownAssetReferenceScanner.ResolvedRewrite(reference, uri))
            .toList());
        assertThat(rewritten).contains("![inline](" + uri + ")");
        assertThat(rewritten).contains("![reference](" + uri + ")");
        assertThat(rewritten).contains("![html](" + uri + ")");
        assertThat(rewritten).contains("![架构图](" + uri + ")");
    }

    @Test
    void unescapesDestinationAndIgnoresFencedCodeAndRemoteImages() {
        String markdown = """
            ~~~
            ![[ignored.png]]
            ~~~
            ![escaped](a\\(1\\).png)
            ![remote](https://example.com/a.png)
            """;

        List<MarkdownAssetReference> references = scanner.scan(markdown);

        assertThat(references).singleElement()
            .extracting(MarkdownAssetReference::originalTarget)
            .isEqualTo("a(1).png");
    }

    @Test
    void rejectsLocalAbsolutePaths() {
        assertThatThrownBy(() -> scanner.scan("![x](file:///Users/u/x.png)"))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(30011);
        assertThatThrownBy(() -> scanner.scan("![x](C:\\\\Users\\\\u\\\\x.png)"))
            .isInstanceOf(BusinessException.class);
    }
}
