package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;

/**
 * 权限不足异常 (403)
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}