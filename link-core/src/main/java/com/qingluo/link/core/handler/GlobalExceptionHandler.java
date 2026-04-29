package com.qingluo.link.core.handler;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.core.exception.BusinessException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器。
 *
 * <p>统一把业务异常、登录鉴权异常、参数校验异常和未知异常转换为标准 {@link Result} 响应。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param e 业务异常，包含业务错误码和 HTTP 状态码
     * @param request 当前 HTTP 请求
     * @return 标准错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.error("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
            .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理未登录或登录态失效异常。
     *
     * @param e Sa-Token 未登录异常
     * @return 401 标准错误响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Object>> handleNotLoginException(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Result.error(401, "未登录或登录已过期"));
    }

    /**
     * 处理角色权限不足异常。
     *
     * @param e Sa-Token 角色校验异常
     * @return 403 标准错误响应
     */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Object>> handleNotRoleException(NotRoleException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Result.error(403, "权限不足"));
    }

    /**
     * 处理请求参数校验异常。
     *
     * @param e 参数绑定和校验异常
     * @return 400 标准错误响应，优先返回第一个字段错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest()
            .body(Result.error(400, message));
    }

    /**
     * 处理未被业务分支捕获的系统异常。
     *
     * <p>响应中不暴露堆栈细节，详细错误只写服务端日志。
     *
     * @param e 未知异常
     * @param request 当前 HTTP 请求
     * @return 500 标准错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(50001, "系统内部错误"));
    }
}
