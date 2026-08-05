package com.qingluo.link.service.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Java mirror of the document suffixes that the Python parser can actually process.
 */
public final class DocumentFileTypeContract {

    private static final List<String> SUPPORTED_SUFFIX_LIST =
        List.of("md", "markdown", "pdf", "docx", "html", "htm");
    private static final Set<String> SUPPORTED_SUFFIXES = Set.copyOf(SUPPORTED_SUFFIX_LIST);
    private static final Set<String> LEGACY_DEFAULT_SUFFIXES =
        Set.of("md", "markdown", "pdf", "docx", "txt");

    private DocumentFileTypeContract() {
    }

    public static LinkedHashSet<String> supportedSuffixes() {
        return new LinkedHashSet<>(SUPPORTED_SUFFIX_LIST);
    }

    public static boolean isSupported(String suffix) {
        return StringUtils.hasText(suffix)
            && SUPPORTED_SUFFIXES.contains(suffix.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The former shipped default is migrated automatically; any other unsupported deployment
     * value fails closed so a custom environment cannot advertise a parser capability Python lacks.
     */
    public static LinkedHashSet<String> resolveDeploymentSuffixes(Set<String> configuredSuffixes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (configuredSuffixes != null) {
            configuredSuffixes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(normalized::add);
        }
        if (normalized.equals(LEGACY_DEFAULT_SUFFIXES)) {
            return supportedSuffixes();
        }
        if (normalized.isEmpty() || !SUPPORTED_SUFFIXES.containsAll(normalized)) {
            throw new IllegalStateException(
                "tolink.document-file.allowed-suffixes must be a non-empty subset of "
                    + SUPPORTED_SUFFIX_LIST);
        }
        return normalized;
    }
}
