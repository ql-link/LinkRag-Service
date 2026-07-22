package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarkdownAssetPathResolverTest {

    private final MarkdownAssetPathResolver resolver = new MarkdownAssetPathResolver();

    @Test
    void shallowModeUsesFiniteBasenameCandidatesAndReusesOneAsset() {
        MarkdownAssetFile asset = asset("Pasted image.png");
        Map<String, MarkdownAssetFile> files = Map.of("Pasted image.png", asset);
        List<MarkdownAssetReference> references = List.of(
            reference("../old/Pasted%20image.png", MarkdownAssetReference.Syntax.MARKDOWN),
            reference("other/Pasted%20image.png", MarkdownAssetReference.Syntax.HTML));

        List<MarkdownAssetPathResolver.Resolution> result = resolver.resolveAll(
            references,
            MarkdownAssetMatchMode.SHALLOW_BASENAME,
            "guide.md",
            Set.of("Pasted image.png"),
            files);

        assertThat(result).allMatch(item -> item.status() == MarkdownAssetPathResolver.Status.MATCHED);
        assertThat(result).allMatch(item -> item.asset() == asset);
        assertThat(result.get(0).candidates()).containsExactly(
            "Pasted%20image.png", "Pasted image.png");
    }

    @Test
    void shallowModeTreatsLiteralAndDecodedFilesAsAmbiguousAndKeepsPlusLiteral() {
        Map<String, MarkdownAssetFile> files = new LinkedHashMap<>();
        files.put("a%20b.png", asset("a%20b.png"));
        files.put("a b.png", asset("a b.png"));
        files.put("a+b.png", asset("a+b.png"));

        var ambiguous = resolver.resolveShallow(
            reference("a%20b.png", MarkdownAssetReference.Syntax.MARKDOWN),
            files.keySet(),
            files);
        var plus = resolver.resolveShallow(
            reference("a+b.png", MarkdownAssetReference.Syntax.MARKDOWN),
            files.keySet(),
            files);

        assertThat(ambiguous.status()).isEqualTo(MarkdownAssetPathResolver.Status.AMBIGUOUS);
        assertThat(ambiguous.existingCandidates()).containsExactly("a%20b.png", "a b.png");
        assertThat(plus.status()).isEqualTo(MarkdownAssetPathResolver.Status.MATCHED);
        assertThat(plus.normalizedTarget()).isEqualTo("a+b.png");
    }

    @Test
    void fullPathModeResolvesRelativePathsWithoutBasenameFallback() {
        MarkdownAssetFile image = asset("images/a.png");
        var matched = resolver.resolveFull(
            reference("../images/a.png", MarkdownAssetReference.Syntax.MARKDOWN),
            "docs/guide.md",
            Set.of("images/a.png", "other/a.png"),
            Map.of("images/a.png", image));
        var missing = resolver.resolveFull(
            reference("images/a.png", MarkdownAssetReference.Syntax.MARKDOWN),
            "docs/guide.md",
            Set.of("other/a.png"),
            Map.of());

        assertThat(matched.status()).isEqualTo(MarkdownAssetPathResolver.Status.MATCHED);
        assertThat(matched.normalizedTarget()).isEqualTo("images/a.png");
        assertThat(missing.status()).isEqualTo(MarkdownAssetPathResolver.Status.MISSING);
        assertThat(missing.normalizedTarget()).isEqualTo("docs/images/a.png");
    }

    @Test
    void fullPathObsidianRequiresUniqueCandidate() {
        var result = resolver.resolveFull(
            reference("Pasted image.png", MarkdownAssetReference.Syntax.OBSIDIAN),
            "notes/guide.md",
            Set.of("a/Pasted image.png", "b/Pasted image.png"),
            Map.of());

        assertThat(result.status()).isEqualTo(MarkdownAssetPathResolver.Status.AMBIGUOUS);
        assertThat(result.existingCandidates()).hasSize(2);
    }

    private MarkdownAssetReference reference(String target, MarkdownAssetReference.Syntax syntax) {
        return new MarkdownAssetReference(syntax, target, "", 0, 1);
    }

    private MarkdownAssetFile asset(String path) {
        return new MarkdownAssetFile(path, path, Path.of("/tmp/" + path.replace('/', '-')),
            "image/png", 8);
    }
}
