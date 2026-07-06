package com.qingluo.link.service.impl.document.markdown;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MarkdownAssetManifest(
    List<String> missingAssets,
    Map<String, String> rewrittenAssets
) {
    public MarkdownAssetManifest {
        missingAssets = missingAssets == null ? List.of() : List.copyOf(missingAssets);
        rewrittenAssets = rewrittenAssets == null ? Map.of() : new LinkedHashMap<>(rewrittenAssets);
    }

    public boolean hasMissingAssets() {
        return !missingAssets.isEmpty();
    }
}
