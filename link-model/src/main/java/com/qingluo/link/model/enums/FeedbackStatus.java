package com.qingluo.link.model.enums;

import java.util.Locale;

public enum FeedbackStatus {
    PENDING,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public static FeedbackStatus of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("feedback status is required");
        }
        return FeedbackStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
