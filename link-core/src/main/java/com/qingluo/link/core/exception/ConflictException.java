package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;

/**
 * 资源冲突异常 (409)
 */
public class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}