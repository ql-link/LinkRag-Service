package com.qingluo.link.core.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 错误响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private int code;
    private String message;
    private String detail;
    private LocalDateTime timestamp;
    private String path;
    private String requestId;
}