package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.RecallStreamRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户态召回网关服务。
 *
 * <p>{@link #recall} 的同步阶段（建流前）完成用户状态校验、限流、datasetIds 归属校验/展开；
 * 校验失败抛 {@code BusinessException} 由全局异常处理器转 HTTP 错误（此时尚未建流、未签 JWT、未调 Python）。
 * 校验通过后创建 {@link SseEmitter} 并在转发线程池异步调用 Python，建流后的任何错误以 SSE error 事件表达。</p>
 */
public interface RecallService {

    /**
     * 发起用户态流式召回。
     *
     * @param userId  当前登录用户 ID（由 Controller 从登录态取得）
     * @param request 召回请求（query + datasetIds）
     * @return 前端 SSE 发射器
     */
    SseEmitter recall(Long userId, RecallStreamRequest request);
}
