package com.qingluo.link.model.enums;

import java.util.Locale;

public enum FeedbackType {
    BUG,
    FEATURE,
    EXPERIENCE,
    OTHER;

    public static FeedbackType of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }
        return FeedbackType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
