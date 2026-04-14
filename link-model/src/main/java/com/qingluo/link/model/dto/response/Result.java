package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应包装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应")
public class Result<T> {

    @Schema(description = "状态码", example = "200")
    private int code;

    @Schema(description = "消息", example = "success")
    private String message;

    @Schema(description = "数据")
    private T data;

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> ok(T data) {
        return success(data);
    }

    /**
     * 错误响应
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 错误响应（带 HTTP 状态码）
     */
    public static <T> Result<T> error(int code, String message, int httpStatus) {
        return new Result<>(code, message, null);
    }
}