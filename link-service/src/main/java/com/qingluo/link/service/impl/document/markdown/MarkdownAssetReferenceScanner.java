package com.qingluo.link.service.impl.document.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts local image references from Markdown text.
 */
public class MarkdownAssetReferenceScanner {

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\(([^\\n)]*)\\)");
    private static final Pattern MARKDOWN_REFERENCE_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\[([^\\]]*)]");
    private static final Pattern MARKDOWN_REFERENCE_DEFINITION = Pattern.compile("(?m)^ {0,3}\\[([^\\]]+)]:\\s*(\\S.*)$");
    private static final Pattern HTML_IMAGE = Pattern.compile("<img[^>]*\\bsrc\\s*=\\s*([\"'])(.*?)\\1[^>]*>",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_TITLE = Pattern.compile("(?s)^(.*?)(?:\\s+(\"[^\"]*\"|'[^']*'|\\([^()]*\\)))\\s*$");

    public List<MarkdownAssetReference> scan(String markdown) {
        Map<String, MarkdownAssetReference> references = new LinkedHashMap<>();
        boolean[] excluded = excludedRanges(markdown);
        collectInline(markdown, excluded, references);
        collectHtml(markdown, excluded, references);
        collectReferenceStyle(markdown, excluded, references);
        return new ArrayList<>(references.values());
    }

    public String rewrite(String markdown, Map<String, String> urlsByPath) {
        if (markdown == null || urlsByPath == null || urlsByPath.isEmpty()) {
            return markdown;
        }
        String rewritten = rewriteInline(markdown, urlsByPath);
        rewritten = rewriteHtml(rewritten, urlsByPath);
        return rewriteReferenceDefinitions(rewritten, urlsByPath);
    }

    private void collectInline(String markdown, boolean[] excluded, Map<String, MarkdownAssetReference> references) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            Destination destination = parseDestination(matcher.group(1));
            if (destination == null || !MarkdownAssetPathNormalizer.isLocalAssetPath(destination.rawPath())) {
                continue;
            }
            putReference(references, destination.rawPath());
        }
    }

    private void collectHtml(String markdown, boolean[] excluded, Map<String, MarkdownAssetReference> references) {
        Matcher matcher = HTML_IMAGE.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            String raw = matcher.group(2);
            if (!MarkdownAssetPathNormalizer.isLocalAssetPath(raw)) {
                continue;
            }
            putReference(references, raw);
        }
    }

    private void collectReferenceStyle(String markdown, boolean[] excluded, Map<String, MarkdownAssetReference> references) {
        Set<String> imageLabels = imageReferenceLabels(markdown, excluded);
        if (imageLabels.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Destination> entry : referenceDefinitions(markdown, excluded).entrySet()) {
            if (!imageLabels.contains(entry.getKey())) {
                continue;
            }
            Destination destination = entry.getValue();
            if (!MarkdownAssetPathNormalizer.isLocalAssetPath(destination.rawPath())) {
                continue;
            }
            putReference(references, destination.rawPath());
        }
    }

    private String rewriteInline(String markdown, Map<String, String> urlsByPath) {
        boolean[] excluded = excludedRanges(markdown);
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            Destination destination = parseDestination(matcher.group(1));
            if (destination == null || !MarkdownAssetPathNormalizer.isLocalAssetPath(destination.rawPath())) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(destination.rawPath());
            String replacement = urlsByPath.get(normalized);
            if (replacement == null) {
                continue;
            }
            result.append(markdown, last, matcher.start(1) + destination.start());
            result.append(replacement);
            last = matcher.start(1) + destination.end();
        }
        result.append(markdown, last, markdown.length());
        return result.toString();
    }

    private String rewriteHtml(String markdown, Map<String, String> urlsByPath) {
        boolean[] excluded = excludedRanges(markdown);
        Matcher matcher = HTML_IMAGE.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            String raw = matcher.group(2);
            if (!MarkdownAssetPathNormalizer.isLocalAssetPath(raw)) {
                continue;
            }
            String normalized = MarkdownAssetPathNormalizer.normalize(raw);
            String replacement = urlsByPath.get(normalized);
            if (replacement == null) {
                continue;
            }
            result.append(markdown, last, matcher.start(2));
            result.append(replacement);
            last = matcher.end(2);
        }
        result.append(markdown, last, markdown.length());
        return result.toString();
    }

    private String rewriteReferenceDefinitions(String markdown, Map<String, String> urlsByPath) {
        boolean[] excluded = excludedRanges(markdown);
        Set<String> imageLabels = imageReferenceLabels(markdown, excluded);
        if (imageLabels.isEmpty()) {
            return markdown;
        }
        Matcher matcher = MARKDOWN_REFERENCE_DEFINITION.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            String key = normalizeLabel(matcher.group(1));
            if (!imageLabels.contains(key)) {
                continue;
            }
            Destination destination = parseDestination(matcher.group(2));
            if (destination == null || !MarkdownAssetPathNormalizer.isLocalAssetPath(destination.rawPath())) {
                continue;
            }
            String replacement = urlsByPath.get(MarkdownAssetPathNormalizer.normalize(destination.rawPath()));
            if (replacement == null) {
                continue;
            }
            result.append(markdown, last, matcher.start(2) + destination.start());
            result.append(replacement);
            last = matcher.start(2) + destination.end();
        }
        result.append(markdown, last, markdown.length());
        return result.toString();
    }

    private Map<String, Destination> referenceDefinitions(String markdown, boolean[] excluded) {
        Map<String, Destination> definitions = new LinkedHashMap<>();
        Matcher matcher = MARKDOWN_REFERENCE_DEFINITION.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            Destination destination = parseDestination(matcher.group(2));
            if (destination != null) {
                definitions.putIfAbsent(normalizeLabel(matcher.group(1)), destination);
            }
        }
        return definitions;
    }

    private Set<String> imageReferenceLabels(String markdown, boolean[] excluded) {
        Set<String> labels = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_REFERENCE_IMAGE.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) {
            if (isExcluded(excluded, matcher.start())) {
                continue;
            }
            String explicit = matcher.group(2);
            String label = explicit.isEmpty() ? matcher.group(1) : explicit;
            labels.add(normalizeLabel(label));
        }
        return labels;
    }

    private void putReference(Map<String, MarkdownAssetReference> references, String rawPath) {
        String normalized = MarkdownAssetPathNormalizer.normalize(rawPath);
        references.putIfAbsent(normalized, new MarkdownAssetReference(rawPath, normalized));
    }

    private Destination parseDestination(String value) {
        if (value == null) {
            return null;
        }
        int start = firstNonWhitespace(value);
        if (start >= value.length()) {
            return null;
        }
        if (value.charAt(start) == '<') {
            int end = value.indexOf('>', start + 1);
            if (end < 0) {
                return null;
            }
            return new Destination(value.substring(start + 1, end), start + 1, end);
        }
        String tail = value.substring(start).trim();
        Matcher title = TRAILING_TITLE.matcher(tail);
        if (title.matches()) {
            tail = title.group(1).trim();
        }
        if (tail.isEmpty()) {
            return null;
        }
        int end = start + tail.length();
        return new Destination(tail, start, end);
    }

    private int firstNonWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return value.length();
    }

    private String normalizeLabel(String label) {
        return (label == null ? "" : label.trim().replaceAll("\\s+", " ")).toLowerCase(Locale.ROOT);
    }

    private boolean[] excludedRanges(String markdown) {
        String value = markdown == null ? "" : markdown;
        boolean[] excluded = new boolean[value.length()];
        markFencedCodeBlocks(value, excluded);
        markInlineCodeSpans(value, excluded);
        return excluded;
    }

    private void markFencedCodeBlocks(String markdown, boolean[] excluded) {
        int lineStart = 0;
        boolean inFence = false;
        char fenceChar = 0;
        int fenceLength = 0;
        while (lineStart < markdown.length()) {
            int lineEnd = markdown.indexOf('\n', lineStart);
            int nextLine = lineEnd >= 0 ? lineEnd + 1 : markdown.length();
            int contentEnd = lineEnd >= 0 ? lineEnd : markdown.length();
            if (contentEnd > lineStart && markdown.charAt(contentEnd - 1) == '\r') {
                contentEnd--;
            }
            String line = markdown.substring(lineStart, contentEnd);
            Fence fence = fence(line);
            if (inFence) {
                mark(excluded, lineStart, nextLine);
                if (fence != null && fence.character() == fenceChar && fence.length() >= fenceLength
                    && fence.trailing().trim().isEmpty()) {
                    inFence = false;
                }
            } else if (fence != null) {
                inFence = true;
                fenceChar = fence.character();
                fenceLength = fence.length();
                mark(excluded, lineStart, nextLine);
            }
            lineStart = nextLine;
        }
    }

    private void markInlineCodeSpans(String markdown, boolean[] excluded) {
        int i = 0;
        while (i < markdown.length()) {
            if (markdown.charAt(i) != '`' || isExcluded(excluded, i)) {
                i++;
                continue;
            }
            int runEnd = countRun(markdown, i, '`');
            int runLength = runEnd - i;
            int close = findClosingBackticks(markdown, runEnd, runLength, excluded);
            if (close < 0) {
                i = runEnd;
                continue;
            }
            mark(excluded, i, close + runLength);
            i = close + runLength;
        }
    }

    private int findClosingBackticks(String markdown, int start, int runLength, boolean[] excluded) {
        int i = start;
        while (i < markdown.length()) {
            if (markdown.charAt(i) != '`' || isExcluded(excluded, i)) {
                i++;
                continue;
            }
            int runEnd = countRun(markdown, i, '`');
            if (runEnd - i == runLength) {
                return i;
            }
            i = runEnd;
        }
        return -1;
    }

    private int countRun(String value, int start, char expected) {
        int index = start;
        while (index < value.length() && value.charAt(index) == expected) {
            index++;
        }
        return index;
    }

    private Fence fence(String line) {
        int index = 0;
        int spaces = 0;
        while (index < line.length() && line.charAt(index) == ' ' && spaces < 4) {
            index++;
            spaces++;
        }
        if (spaces > 3 || index >= line.length()) {
            return null;
        }
        char ch = line.charAt(index);
        if (ch != '`' && ch != '~') {
            return null;
        }
        int runEnd = countRun(line, index, ch);
        int length = runEnd - index;
        if (length < 3) {
            return null;
        }
        return new Fence(ch, length, line.substring(runEnd));
    }

    private void mark(boolean[] excluded, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(excluded.length, end);
        for (int i = safeStart; i < safeEnd; i++) {
            excluded[i] = true;
        }
    }

    private boolean isExcluded(boolean[] excluded, int index) {
        return index >= 0 && index < excluded.length && excluded[index];
    }

    private record Destination(String rawPath, int start, int end) {
    }

    private record Fence(char character, int length, String trailing) {
    }
}
