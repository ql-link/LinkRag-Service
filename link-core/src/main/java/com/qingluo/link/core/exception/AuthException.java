package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;

/**
 * 认证异常 (401)
 */
public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}