package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.RecallStreamRequest;
import com.qingluo.link.service.RecallService;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户态召回 SSE 网关。
 *
 * <p>控制器极薄：同步阶段（{@code @SaCheckLogin} 登录校验、{@code @Valid} 参数校验、RecallService 的
 * 用户状态/限流/数据集权限校验）都在返回 {@link SseEmitter} 之前完成，任何异常由 GlobalExceptionHandler
 * 转为 HTTP 错误（建流前）。校验通过后返回 SseEmitter，调用 Python 与结果转发在转发线程池异步进行（建流后 SSE）。</p>
 */
@RestController
@RequiredArgsConstructor
public class RecallController {

    private final RecallService recallService;

    @PostMapping(value = "/api/v1/recall/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public SseEmitter recallStream(@Valid @RequestBody RecallStreamRequest request, HttpServletResponse response) {
        // 关闭缓存，配合部署网关关闭缓冲，确保 SSE 事件即时下发（Content-Type 由 produces 指定）。
        response.setHeader("Cache-Control", "no-cache");
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return recallService.recall(userId, request);
    }
}
