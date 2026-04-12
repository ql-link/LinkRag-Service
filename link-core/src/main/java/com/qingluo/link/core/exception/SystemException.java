package com.qingluo.link.core.exception;

/**
 * 系统异常 (500) - 服务端内部错误
 */
public class SystemException extends RuntimeException {

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}