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
 * <p>签发短期 token 供前端直连 Python 召回 SSE（前端凭 token 直连 Python 的 {@code /api/v1/recall/stream}）。
 * Java 只做 Sa-Token 鉴权 + 数据集归属校验 + 签发，不代理/中转 SSE 流内容。</p>
 *
 * <p>旧的 Java 中转代理链路（{@code RecallController}）已于 LINK-122 废弃移除，召回统一走前端直连 Python。</p>
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
