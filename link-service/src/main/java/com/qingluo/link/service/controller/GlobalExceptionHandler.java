package com.qingluo.link.service.controller;

import com.qingluo.link.core.enums.ErrorCode;
import com.qingluo.link.core.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * HTTP 状态码映射策略：
 *   NotFoundException      → 404
 *   AuthException         → 401
 *   ForbiddenException    → 403
 *   ConflictException     → 409
 *   BusinessException     → 400
 *   SystemException       → 500
 *   未捕获异常             → 500
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * NotFoundException → 404
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, e.getErrorCode(), e.getDetail(), null);
    }

    /**
     * AuthException → 401
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException e) {
        log.warn("认证失败: {}", e.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getErrorCode(), e.getDetail(), null);
    }

    /**
     * ForbiddenException → 403
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException e) {
        log.warn("权限不足: {}", e.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, e.getErrorCode(), e.getDetail(), null);
    }

    /**
     * ConflictException → 409
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException e) {
        log.warn("资源冲突: {}", e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, e.getErrorCode(), e.getDetail(), null);
    }

    /**
     * 其余 BusinessException（未细分子类） → 400
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getDetail(), null);
    }

    /**
     * JSR-303 参数校验失败 → 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.groupingBy(
                fe -> fe.getField(),
                Collectors.mapping(
                    fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "参数错误",
                    Collectors.joining("; ")
                )
            ));

        ErrorResponse response = ErrorResponse.builder()
            .code(400)
            .message("请求参数错误")
            .detail("参数校验失败")
            .data(fieldErrors)
            .timestamp(LocalDateTime.now())
            .path(getRequestPath())
            .requestId(getRequestId())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 系统异常（数据库、缓存等基础设施故障） → 500
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ErrorResponse> handleSystemException(SystemException e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_ERROR, "系统内部错误，请联系管理员", null);
    }

    /**
     * 兜底：未捕获异常 → 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("未捕获异常: {}", e.getMessage(), e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, null, "系统内部错误，请联系管理员", null);
    }

    // ---- 私有工具方法 ----

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus httpStatus,
                                                        ErrorCode errorCode,
                                                        String detail,
                                                        Map<String, Object> data) {
        ErrorResponse response = ErrorResponse.builder()
            .code(errorCode != null ? errorCode.getCode() : httpStatus.value())
            .message(errorCode != null ? errorCode.getMessage() : httpStatus.getReasonPhrase())
            .detail(detail)
            .data(data)
            .timestamp(LocalDateTime.now())
            .path(getRequestPath())
            .requestId(getRequestId())
            .build();
        return ResponseEntity.status(httpStatus).body(response);
    }

    private String getRequestPath() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest().getRequestURI();
        }
        return null;
    }

    private String getRequestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            String rid = request.getHeader("X-Request-Id");
            return rid;
        }
        return null;
    }
}