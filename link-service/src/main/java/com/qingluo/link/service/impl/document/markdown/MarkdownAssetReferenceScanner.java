package com.qingluo.link.service.impl.document.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts local image references from Markdown text.
 */
public class MarkdownAssetReferenceScanner {

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("<img[^>]*\\bsrc=[\"']([^\"'>\\s]+)[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE);

    public List<MarkdownAssetReference> scan(String markdown) {
        Map<String, MarkdownAssetReference> references = new LinkedHashMap<>();
        collect(markdown, MARKDOWN_IMAGE, references);
        collect(markdown, HTML_IMAGE, references);
        return new ArrayList<>(references.values());
    }

    public String rewrite(String markdown, Map<String, String> urlsByPath) {
        if (markdown == null || urlsByPath == null || urlsByPath.isEmpty()) {
            return markdown;
        }
        String rewritten = rewrite(markdown, MARKDOWN_IMAGE, urlsByPath);
        return rewrite(rewritten, HTML_IMAGE, urlsByPath);
    }

    private void collect(String markdown, Pattern pattern, Map<String, MarkdownAssetReference> references) {
        Matcher matcher = pattern.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!MarkdownAssetPathNormalizer.isLocalAssetPath(raw)) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(raw);
            references.putIfAbsent(normalized, new MarkdownAssetReference(raw, normalized));
        }
    }

    private String rewrite(String markdown, Pattern pattern, Map<String, String> urlsByPath) {
        Matcher matcher = pattern.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!MarkdownAssetPathNormalizer.isLocalAssetPath(raw)) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(raw);
            String replacement = urlsByPath.get(normalized);
            if (replacement == null) {
                continue;
            }
            result.append(markdown, last, matcher.start(1));
            result.append(replacement);
            last = matcher.end(1);
        }
        result.append(markdown, last, markdown.length());
        return result.toString();
    }
}
