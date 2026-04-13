package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;

/**
 * 冲突异常
 */
public class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ConflictException duplicateUserConfig() {
        return new ConflictException(ErrorCode.DUPLICATE_USER_CONFIG);
    }
}