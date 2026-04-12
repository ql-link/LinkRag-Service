package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;

/**
 * 资源不存在异常 (404)
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}