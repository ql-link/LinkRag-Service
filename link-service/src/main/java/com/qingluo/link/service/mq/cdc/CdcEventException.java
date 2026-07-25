package com.qingluo.link.service.mq.cdc;

public class CdcEventException extends IllegalArgumentException {

    private final String reason;

    public CdcEventException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CdcEventException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
