package com.qingluo.link.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一成功响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
            .code(200)
            .message("success")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> Result<T> created(T data) {
        return Result.<T>builder()
            .code(201)
            .message("success")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
}