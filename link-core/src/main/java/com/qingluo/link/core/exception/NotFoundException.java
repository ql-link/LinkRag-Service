package com.qingluo.link.core.exception;

import com.qingluo.link.model.enums.ErrorCode;

/**
 * 资源不存在异常
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static NotFoundException providerNotFound() {
        return new NotFoundException(ErrorCode.PROVIDER_NOT_FOUND);
    }

    public static NotFoundException userConfigNotFound() {
        return new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND);
    }

    public static NotFoundException conversationNotFound() {
        return new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    public static NotFoundException userNotFound() {
        return new NotFoundException(ErrorCode.USER_NOT_FOUND);
    }
}