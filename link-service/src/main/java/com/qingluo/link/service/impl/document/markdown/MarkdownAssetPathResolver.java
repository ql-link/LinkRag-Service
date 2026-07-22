package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarkdownAssetPathResolver {

    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Set<String> SUPPORTED_SUFFIXES =
        Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff");

    public List<Resolution> resolveAll(
            List<MarkdownAssetReference> references,
            MarkdownAssetMatchMode mode,
            String documentPath,
            Collection<String> inventoryPaths,
            Map<String, MarkdownAssetFile> byteAssets) {
        Set<String> inventory = new LinkedHashSet<>(inventoryPaths);
        List<Resolution> result = new ArrayList<>(references.size());
        for (MarkdownAssetReference reference : references) {
            result.add(mode == MarkdownAssetMatchMode.SHALLOW_BASENAME
                ? resolveShallow(reference, inventory, byteAssets)
                : resolveFull(reference, documentPath, inventory, byteAssets));
        }
        return result;
    }

    public Resolution resolveShallow(
            MarkdownAssetReference reference,
            Set<String> inventory,
            Map<String, MarkdownAssetFile> byteAssets) {
        LinkedHashSet<String> candidates = basenameCandidates(reference.originalTarget());
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (inventory.contains(candidate)) {
                existing.add(candidate);
            }
        }
        return finish(reference, candidates, existing, byteAssets);
    }

    public Resolution resolveFull(
            MarkdownAssetReference reference,
            String documentPath,
            Set<String> inventory,
            Map<String, MarkdownAssetFile> byteAssets) {
        String parent = parentOf(documentPath);
        LinkedHashSet<String> targetVariants = targetCandidates(reference.originalTarget());
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String target : targetVariants) {
            candidates.add(resolveFrom(parent, target));
            if (reference.syntax() == MarkdownAssetReference.Syntax.OBSIDIAN) {
                candidates.add(resolveFrom("", target));
            }
        }
        if (reference.syntax() == MarkdownAssetReference.Syntax.OBSIDIAN
            && targetVariants.stream().allMatch(value -> !value.contains("/"))) {
            Set<String> basenames = new LinkedHashSet<>();
            targetVariants.forEach(value -> basenames.add(basename(value)));
            inventory.stream()
                .filter(path -> basenames.contains(basename(path)))
                .forEach(candidates::add);
        }
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (inventory.contains(candidate)) {
                existing.add(candidate);
            }
        }
        return finish(reference, candidates, existing, byteAssets);
    }

    private Resolution finish(
            MarkdownAssetReference reference,
            LinkedHashSet<String> candidates,
            LinkedHashSet<String> existing,
            Map<String, MarkdownAssetFile> byteAssets) {
        if (existing.size() > 1) {
            return new Resolution(reference, Status.AMBIGUOUS, first(candidates),
                List.copyOf(candidates), List.copyOf(existing), null);
        }
        if (existing.isEmpty()) {
            return new Resolution(reference, Status.MISSING, first(candidates),
                List.copyOf(candidates), List.of(), null);
        }
        String path = existing.iterator().next();
        if (!SUPPORTED_SUFFIXES.contains(suffix(path))) {
            return new Resolution(reference, Status.UNSUPPORTED, path,
                List.copyOf(candidates), List.of(path), null);
        }
        MarkdownAssetFile asset = byteAssets.get(path);
        if (asset == null) {
            return new Resolution(reference, Status.MISSING, path,
                List.copyOf(candidates), List.of(path), null);
        }
        return new Resolution(reference, Status.MATCHED, path,
            List.copyOf(candidates), List.of(path), asset);
    }

    public String normalizeCatalogPath(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw invalidPath();
        }
        String value = Normalizer.normalize(raw.trim().replace('\\', '/'), Normalizer.Form.NFC);
        assertNotAbsolute(value);
        Deque<String> parts = new ArrayDeque<>();
        for (String part : value.split("/", -1)) {
            if (!StringUtils.hasText(part) || ".".equals(part) || "..".equals(part)
                || containsControl(part)) {
                throw invalidPath();
            }
            parts.addLast(part);
        }
        return String.join("/", parts);
    }

    public LinkedHashSet<String> basenameCandidates(String target) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String variant : targetCandidates(target)) {
            String name = basename(variant);
            if (StringUtils.hasText(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private LinkedHashSet<String> targetCandidates(String raw) {
        String value = normalizeTarget(raw);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(value);
        String decoded = strictPercentDecode(value);
        if (decoded != null) {
            result.add(Normalizer.normalize(decoded, Normalizer.Form.NFC));
        }
        return result;
    }

    private String normalizeTarget(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw invalidPath();
        }
        String value = Normalizer.normalize(raw.trim().replace('\\', '/'), Normalizer.Form.NFC);
        assertNotAbsolute(value);
        if (containsControl(value)) {
            throw invalidPath();
        }
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        return value;
    }

    private String resolveFrom(String parent, String target) {
        assertNotAbsolute(target);
        Deque<String> parts = new ArrayDeque<>();
        if (StringUtils.hasText(parent)) {
            for (String part : parent.split("/")) {
                if (StringUtils.hasText(part)) {
                    parts.addLast(part);
                }
            }
        }
        for (String part : target.split("/", -1)) {
            if (!StringUtils.hasText(part) || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (parts.isEmpty()) {
                    throw new BusinessException(ErrorCode.ASSET_PATH_OUTSIDE_ROOT);
                }
                parts.removeLast();
                continue;
            }
            if (containsControl(part)) {
                throw invalidPath();
            }
            parts.addLast(part);
        }
        if (parts.isEmpty()) {
            throw invalidPath();
        }
        return String.join("/", parts);
    }

    private String strictPercentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < value.length();) {
            char ch = value.charAt(i);
            if (ch == '%') {
                if (i + 2 >= value.length()) {
                    return null;
                }
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    return null;
                }
                bytes.write((high << 4) + low);
                i += 3;
            } else {
                int start = i;
                while (i < value.length() && value.charAt(i) != '%') {
                    i++;
                }
                bytes.writeBytes(value.substring(start, i).getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private void assertNotAbsolute(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("/") || value.startsWith("\\\\") || WINDOWS_ABSOLUTE.matcher(value).matches()
            || lower.startsWith("file:")) {
            throw new BusinessException(ErrorCode.LOCAL_ABSOLUTE_PATH_REJECTED);
        }
    }

    private String parentOf(String path) {
        int index = path == null ? -1 : path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    private String basename(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private String suffix(String path) {
        String name = basename(path);
        int index = name.lastIndexOf('.');
        return index < 0 || index == name.length() - 1
            ? ""
            : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String first(LinkedHashSet<String> values) {
        return values.isEmpty() ? "" : values.iterator().next();
    }

    private boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private BusinessException invalidPath() {
        return new BusinessException(400, "图片路径非法", 400);
    }

    public enum Status {
        MATCHED,
        MISSING,
        AMBIGUOUS,
        UNSUPPORTED
    }

    public record Resolution(
        MarkdownAssetReference reference,
        Status status,
        String normalizedTarget,
        List<String> candidates,
        List<String> existingCandidates,
        MarkdownAssetFile asset
    ) {
    }
}
