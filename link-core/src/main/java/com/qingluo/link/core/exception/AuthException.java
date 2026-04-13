package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;

/**
 * 认证异常
 */
public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static AuthException userNotFound() {
        return new AuthException(ErrorCode.USER_NOT_FOUND);
    }

    public static AuthException invalidPassword() {
        return new AuthException(ErrorCode.INVALID_PASSWORD);
    }

    public static AuthException accountDisabled() {
        return new AuthException(ErrorCode.AUTH_DISABLED);
    }
}