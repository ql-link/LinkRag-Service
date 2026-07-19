package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * 业务异常基类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.details = Collections.emptyMap();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.details = Collections.emptyMap();
    }

    public BusinessException(ErrorCode errorCode, Map<String, ?> details) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.details = immutableDetails(details);
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, ?> details) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.details = immutableDetails(details);
    }

    public BusinessException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = Collections.emptyMap();
    }

    private static Map<String, Object> immutableDetails(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
