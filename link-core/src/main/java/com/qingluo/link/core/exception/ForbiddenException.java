package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;

/**
 * 禁止访问异常
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ForbiddenException unauthorizedAccess() {
        return new ForbiddenException(ErrorCode.UNAUTHORIZED_ACCESS);
    }
}