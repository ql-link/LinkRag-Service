package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 基础业务异常（对应 HTTP 4xx，由子类决定具体状态码）
 * 使用 @Getter 而非 @Data：异常字段均为 final，不需要 setter；
 * 避免 @Data 生成可能暴露敏感信息的 toString()。
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    private final String detail;


    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }


    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }


    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(String.format("[%d] %s", errorCode.getCode(), detail), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}