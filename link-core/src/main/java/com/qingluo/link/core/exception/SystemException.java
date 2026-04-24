package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;

/**
 * 系统异常
 */
public class SystemException extends BusinessException {

    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static SystemException unknownError() {
        return new SystemException(ErrorCode.UNKNOWN_ERROR);
    }

    public static SystemException unknownError(String message) {
        return new SystemException(ErrorCode.UNKNOWN_ERROR, message);
    }
}