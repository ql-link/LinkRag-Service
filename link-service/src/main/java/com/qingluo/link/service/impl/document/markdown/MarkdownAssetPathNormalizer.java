package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;

/**
 * Normalizes Markdown local image paths into safe object-key relative paths.
 */
public final class MarkdownAssetPathNormalizer {

    private MarkdownAssetPathNormalizer() {
    }

    public static String normalize(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw invalid();
        }
        String value = stripFragmentAndQuery(rawPath.trim());
        value = percentDecode(value).replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        value = value.replaceAll("/{2,}", "/");
        if (!StringUtils.hasText(value) || value.startsWith("/") || value.matches("^[A-Za-z]:/.*")) {
            throw invalid();
        }
        String[] parts = value.split("/");
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part) || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part) || containsControlCharacter(part)) {
                throw invalid();
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(part);
        }
        if (normalized.length() == 0) {
            throw invalid();
        }
        return normalized.toString();
    }

    public static boolean isLocalAssetPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return false;
        }
        String value = rawPath.trim().toLowerCase();
        return !(value.startsWith("http://")
            || value.startsWith("https://")
            || value.startsWith("data:")
            || value.startsWith("file:")
            || value.startsWith("#"));
    }

    private static String stripFragmentAndQuery(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int end = value.length();
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        return value.substring(0, end);
    }

    private static String percentDecode(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            out.writeBytes(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static BusinessException invalid() {
        return new BusinessException(400, "图片路径非法", 400);
    }
}
