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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.error("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
            .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Object>> handleNotLoginException(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Result.error(401, "未登录或登录已过期"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Object>> handleNotRoleException(NotRoleException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Result.error(403, "权限不足"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest()
            .body(Result.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(50001, "系统内部错误"));
    }
}