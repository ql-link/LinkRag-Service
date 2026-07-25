package com.qingluo.link.service.impl.document.markdown;

public record MarkdownAssetReference(
    Syntax syntax,
    String originalTarget,
    String alt,
    int startOffset,
    int endOffset
) {
    public enum Syntax {
        MARKDOWN,
        MARKDOWN_REFERENCE,
        HTML,
        OBSIDIAN
    }
}
