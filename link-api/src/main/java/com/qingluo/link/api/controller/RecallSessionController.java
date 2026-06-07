package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.RecallSessionRequest;
import com.qingluo.link.model.dto.response.RecallSessionResponse;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.RecallSessionService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 召回 session token 签发接口（LINK-104）。
 *
 * <p>签发短期 token 供前端直连 Python 召回 SSE（{@code POST /api/v1/recall/stream}）。Java 只做
 * Sa-Token 鉴权 + 数据集归属校验 + 签发，不代理/中转 SSE 流内容。</p>
 *
 * <p>原 {@code POST /api/v1/recall/stream} 内部代理链路（{@link RecallController}）保持不变，本接口为加法。</p>
 */
@RestController
@RequiredArgsConstructor
public class RecallSessionController {

    private final RecallSessionService recallSessionService;

    @PostMapping("/api/v1/recall/sessions")
    @SaCheckLogin
    public Result<RecallSessionResponse> issue(@Valid @RequestBody RecallSessionRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(recallSessionService.issue(userId, request));
    }
}
