package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarkdownAssetReferenceScanner {

    private static final Pattern INLINE_IMAGE = Pattern.compile(
        "!\\[((?:\\\\.|[^\\]])*)]\\(((?:\\\\.|[^\\)])*)\\)");
    private static final Pattern REFERENCE_IMAGE = Pattern.compile(
        "!\\[((?:\\\\.|[^\\]])*)]\\[((?:\\\\.|[^\\]])*)]");
    private static final Pattern REFERENCE_DEFINITION = Pattern.compile(
        "(?m)^ {0,3}\\[((?:\\\\.|[^\\]])+)]:\\s*(.+)$");
    private static final Pattern HTML_IMG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern HTML_SRC = Pattern.compile(
        "(?is)\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern HTML_ALT = Pattern.compile(
        "(?is)\\balt\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern OBSIDIAN = Pattern.compile("!\\[\\[((?:\\\\.|[^\\]])+)]]");
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern TRAILING_TITLE = Pattern.compile(
        "(?s)^(.*?)(?:\\s+(\"[^\"]*\"|'[^']*'|\\([^()]*\\)))\\s*$");

    public List<MarkdownAssetReference> scan(String markdown) {
        String source = markdown == null ? "" : markdown;
        boolean[] excluded = excludedRanges(source);
        List<MarkdownAssetReference> result = new ArrayList<>();
        collectInline(source, excluded, result);
        collectReferenceStyle(source, excluded, result);
        collectHtml(source, excluded, result);
        collectObsidian(source, excluded, result);
        result.sort(Comparator.comparingInt(MarkdownAssetReference::startOffset));
        return removeOverlaps(result);
    }

    public String rewrite(String markdown, List<ResolvedRewrite> rewrites) {
        if (rewrites == null || rewrites.isEmpty()) {
            return markdown;
        }
        StringBuilder result = new StringBuilder(markdown);
        rewrites.stream()
            .sorted(Comparator.comparingInt((ResolvedRewrite item) -> item.reference().startOffset()).reversed())
            .forEach(item -> result.replace(
                item.reference().startOffset(),
                item.reference().endOffset(),
                "![" + escapeAlt(item.reference().alt()) + "](" + item.logicalUri() + ")"));
        return result.toString();
    }

    private void collectInline(
            String source, boolean[] excluded, List<MarkdownAssetReference> result) {
        Matcher inline = INLINE_IMAGE.matcher(source);
        while (inline.find()) {
            if (isExcluded(excluded, inline.start())) {
                continue;
            }
            String target = parseDestination(inline.group(2));
            if (isLocalOrThrow(target)) {
                result.add(new MarkdownAssetReference(
                    MarkdownAssetReference.Syntax.MARKDOWN,
                    target,
                    unescape(inline.group(1)),
                    inline.start(),
                    inline.end()));
            }
        }
    }

    private void collectReferenceStyle(
            String source, boolean[] excluded, List<MarkdownAssetReference> result) {
        Map<String, String> definitions = referenceDefinitions(source, excluded);
        Matcher reference = REFERENCE_IMAGE.matcher(source);
        while (reference.find()) {
            if (isExcluded(excluded, reference.start())) {
                continue;
            }
            String explicit = unescape(reference.group(2));
            String label = normalizeLabel(
                StringUtils.hasText(explicit) ? explicit : unescape(reference.group(1)));
            String target = definitions.get(label);
            if (target != null && isLocalOrThrow(target)) {
                result.add(new MarkdownAssetReference(
                    MarkdownAssetReference.Syntax.MARKDOWN_REFERENCE,
                    target,
                    unescape(reference.group(1)),
                    reference.start(),
                    reference.end()));
            }
        }
    }

    private void collectHtml(
            String source, boolean[] excluded, List<MarkdownAssetReference> result) {
        Matcher tags = HTML_IMG.matcher(source);
        while (tags.find()) {
            if (isExcluded(excluded, tags.start())) {
                continue;
            }
            String tag = tags.group();
            Matcher src = HTML_SRC.matcher(tag);
            if (!src.find()) {
                continue;
            }
            String target = htmlDecode(first(src.group(1), src.group(2), src.group(3)));
            if (!isLocalOrThrow(target)) {
                continue;
            }
            Matcher altMatcher = HTML_ALT.matcher(tag);
            String alt = altMatcher.find()
                ? htmlDecode(first(altMatcher.group(1), altMatcher.group(2), altMatcher.group(3)))
                : "";
            result.add(new MarkdownAssetReference(
                MarkdownAssetReference.Syntax.HTML,
                target,
                alt,
                tags.start(),
                tags.end()));
        }
    }

    private void collectObsidian(
            String source, boolean[] excluded, List<MarkdownAssetReference> result) {
        Matcher obsidian = OBSIDIAN.matcher(source);
        while (obsidian.find()) {
            if (isExcluded(excluded, obsidian.start())) {
                continue;
            }
            ObsidianTarget parsed = parseObsidian(obsidian.group(1));
            if (isLocalOrThrow(parsed.target())) {
                result.add(new MarkdownAssetReference(
                    MarkdownAssetReference.Syntax.OBSIDIAN,
                    parsed.target(),
                    parsed.alias(),
                    obsidian.start(),
                    obsidian.end()));
            }
        }
    }

    private Map<String, String> referenceDefinitions(String source, boolean[] excluded) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher definitions = REFERENCE_DEFINITION.matcher(source);
        while (definitions.find()) {
            if (isExcluded(excluded, definitions.start())) {
                continue;
            }
            String target = parseDestination(definitions.group(2));
            if (StringUtils.hasText(target)) {
                result.putIfAbsent(normalizeLabel(unescape(definitions.group(1))), target);
            }
        }
        return result;
    }

    private String parseDestination(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String target = value.trim();
        if (target.startsWith("<")) {
            int close = target.indexOf('>');
            return close > 0 ? unescape(target.substring(1, close)) : "";
        }
        Matcher title = TRAILING_TITLE.matcher(target);
        if (title.matches()) {
            target = title.group(1).trim();
        }
        return unescape(target);
    }

    private boolean isLocalOrThrow(String target) {
        if (!StringUtils.hasText(target)) {
            return false;
        }
        String value = target.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || value.startsWith("/") || value.startsWith("\\\\")
            || WINDOWS_ABSOLUTE.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.LOCAL_ABSOLUTE_PATH_REJECTED);
        }
        if (value.startsWith("#") || value.startsWith("//") || lower.startsWith("http:")
            || lower.startsWith("https:") || lower.startsWith("data:")) {
            return false;
        }
        return !SCHEME.matcher(value).find();
    }

    private List<MarkdownAssetReference> removeOverlaps(List<MarkdownAssetReference> references) {
        List<MarkdownAssetReference> result = new ArrayList<>();
        int end = -1;
        for (MarkdownAssetReference reference : references) {
            if (reference.startOffset() >= end) {
                result.add(reference);
                end = reference.endOffset();
            }
        }
        return result;
    }

    private ObsidianTarget parseObsidian(String body) {
        StringBuilder target = new StringBuilder();
        StringBuilder alias = new StringBuilder();
        boolean escaped = false;
        boolean inAlias = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (escaped) {
                (inAlias ? alias : target).append(ch);
                escaped = false;
            } else if (ch == '\\' && i + 1 < body.length() && body.charAt(i + 1) == '|'
                    && !inAlias) {
                // Markdown tables commonly escape Obsidian's alias separator as \|.
                inAlias = true;
                i++;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '|' && !inAlias) {
                inAlias = true;
            } else {
                (inAlias ? alias : target).append(ch);
            }
        }
        if (escaped) {
            (inAlias ? alias : target).append('\\');
        }
        return new ObsidianTarget(target.toString().trim(), alias.toString().trim());
    }

    private String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value == null ? "" : value;
        }
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                result.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                result.append(ch);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private String htmlDecode(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        Matcher numeric = Pattern.compile("&#(x?[0-9A-Fa-f]+);").matcher(decoded);
        StringBuffer result = new StringBuffer();
        while (numeric.find()) {
            String token = numeric.group(1);
            int radix = token.startsWith("x") || token.startsWith("X") ? 16 : 10;
            String digits = radix == 16 ? token.substring(1) : token;
            try {
                numeric.appendReplacement(result, Matcher.quoteReplacement(
                    new String(Character.toChars(Integer.parseInt(digits, radix)))));
            } catch (RuntimeException e) {
                numeric.appendReplacement(result, Matcher.quoteReplacement(numeric.group()));
            }
        }
        numeric.appendTail(result);
        return result.toString();
    }

    private String escapeAlt(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("]", "\\]");
    }

    private String normalizeLabel(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private boolean[] excludedRanges(String markdown) {
        boolean[] excluded = new boolean[markdown.length()];
        markFencedCodeBlocks(markdown, excluded);
        markInlineCodeSpans(markdown, excluded);
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
        int index = 0;
        while (index < markdown.length()) {
            if (markdown.charAt(index) != 96 || isExcluded(excluded, index)) {
                index++;
                continue;
            }
            int openEnd = countRun(markdown, index, (char) 96);
            int length = openEnd - index;
            int close = findClosingRun(markdown, openEnd, length, excluded);
            if (close < 0) {
                index = openEnd;
                continue;
            }
            mark(excluded, index, close + length);
            index = close + length;
        }
    }

    private int findClosingRun(String value, int start, int length, boolean[] excluded) {
        int index = start;
        while (index < value.length()) {
            if (value.charAt(index) != 96 || isExcluded(excluded, index)) {
                index++;
                continue;
            }
            int end = countRun(value, index, (char) 96);
            if (end - index == length) {
                return index;
            }
            index = end;
        }
        return -1;
    }

    private Fence fence(String line) {
        int index = 0;
        while (index < line.length() && index < 4 && line.charAt(index) == ' ') {
            index++;
        }
        if (index > 3 || index >= line.length()) {
            return null;
        }
        char ch = line.charAt(index);
        if (ch != 96 && ch != '~') {
            return null;
        }
        int end = countRun(line, index, ch);
        return end - index < 3 ? null : new Fence(ch, end - index, line.substring(end));
    }

    private int countRun(String value, int start, char ch) {
        int index = start;
        while (index < value.length() && value.charAt(index) == ch) {
            index++;
        }
        return index;
    }

    private void mark(boolean[] excluded, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(excluded.length, end); i++) {
            excluded[i] = true;
        }
    }

    private boolean isExcluded(boolean[] excluded, int index) {
        return index >= 0 && index < excluded.length && excluded[index];
    }

    public record ResolvedRewrite(MarkdownAssetReference reference, String logicalUri) {
    }

    private record ObsidianTarget(String target, String alias) {
    }

    private record Fence(char character, int length, String trailing) {
    }
}
