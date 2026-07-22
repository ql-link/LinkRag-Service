package com.qingluo.link.service.impl.document.markdown;

import com.qingluo.link.core.exception.BusinessException;
import java.util.Locale;
import org.springframework.util.StringUtils;

public enum MarkdownAssetMatchMode {
    FULL_PATH,
    SHALLOW_BASENAME;

    public static MarkdownAssetMatchMode parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的 Markdown 图片匹配模式", 400);
        }
    }
}
