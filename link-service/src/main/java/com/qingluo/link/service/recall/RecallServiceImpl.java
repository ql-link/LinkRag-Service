package com.qingluo.link.service.recall;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.security.InternalJwtSigner;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.RecallStreamRequest;
import com.qingluo.link.model.dto.response.RecallDoneEvent;
import com.qingluo.link.model.dto.response.RecallErrorEvent;
import com.qingluo.link.model.dto.response.RecallHitDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.RecallSseError;
import com.qingluo.link.service.RecallService;
import com.qingluo.link.service.config.RecallProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户态召回网关编排。
 *
 * <p><b>建流前（同步段）</b>：用户状态 → 限流 → datasetIds 归属校验/展开，任一失败抛 {@link BusinessException}
 * 由全局异常处理器转 HTTP 错误（此时未建流、未签 JWT、未调 Python，满足不变量场景 26/27）。</p>
 *
 * <p><b>建流后</b>：创建 {@link SseEmitter}，在转发线程池异步调用 Python；结果经 {@link RecallUpstreamListener}
 * 回调转为 SSE recall_done/error 并关流。前端断连时取消上游 okhttp 调用。</p>
 */
@Service
@Slf4j
public class RecallServiceImpl implements RecallService {

    private final SysUserMapper sysUserMapper;
    private final RecallScopeResolver scopeResolver;
    private final RecallRateLimiter rateLimiter;
    private final RecallUpstreamClient upstreamClient;
    private final InternalJwtSigner jwtSigner;
    private final RecallProperties properties;

    public RecallServiceImpl(SysUserMapper sysUserMapper,
                             RecallScopeResolver scopeResolver,
                             RecallRateLimiter rateLimiter,
                             RecallUpstreamClient upstreamClient,
                             InternalJwtSigner jwtSigner,
                             RecallProperties properties) {
        this.sysUserMapper = sysUserMapper;
        this.scopeResolver = scopeResolver;
        this.rateLimiter = rateLimiter;
        this.upstreamClient = upstreamClient;
        this.jwtSigner = jwtSigner;
        this.properties = properties;
    }

    @Override
    public SseEmitter recall(Long userId, RecallStreamRequest request) {
        // —— 建流前同步校验 ——
        assertUserActive(userId);
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ErrorCode.RECALL_RATE_LIMITED);
        }
        ResolvedScope scope = scopeResolver.resolve(userId, request.getDatasetIds());

        // —— 校验通过，建流 ——
        // emitter 超时 = 上游整体超时 + 缓冲，确保 okhttp callTimeout（= stream-timeout-ms）先触发并发出
        // RECALL_TIMEOUT，而非前端 SSE 先超时被静默关闭（避免两个等长计时器竞争）。
        SseEmitter emitter = new SseEmitter(properties.getStreamTimeoutMs() + properties.getEmitterTimeoutBufferMs());

        if (scope.emptyOwnership()) {
            // 用户名下无任何数据集：直接返回空候选，不签 JWT、不调 Python（场景 10）。
            // ResponseBodyEmitter 支持在 handler 返回前 send/complete（缓冲后由 Spring flush）。
            emitDone(emitter, List.of());
            return emitter;
        }

        String requestId = UUID.randomUUID().toString();
        // sub / dataset_ids 与发往 Python 的 body 同源，保证自洽（场景 12/13/15）。
        String jwt = jwtSigner.sign(userId, scope.datasetIds(), requestId, Instant.now());
        RecallUpstreamRequest upstream = new RecallUpstreamRequest(request.getQuery(), userId, scope.datasetIds());

        AtomicReference<RecallUpstreamCall> callRef = new AtomicReference<>();
        bindLifecycle(emitter, callRef, userId, requestId);

        RecallUpstreamListener listener = new RecallUpstreamListener() {
            @Override
            public void onDone(List<RecallHitDTO> hits) {
                emitDone(emitter, hits);
            }

            @Override
            public void onError(RecallSseError error) {
                emitError(emitter, error);
            }
        };

        try {
            callRef.set(upstreamClient.stream(upstream, jwt, requestId, listener));
        } catch (RejectedExecutionException e) {
            // 转发线程池已满：建流后以 SSE error 表达（不退回同步、不抛 HTTP 错误）。
            log.warn("召回转发线程池已满, userId={}, requestId={}", userId, requestId);
            emitError(emitter, RecallSseError.RECALL_UPSTREAM_ERROR);
        }
        return emitter;
    }

    /**
     * 复用登录态用户状态：status != 1 视为不可用，拒绝召回（决策③，建流前 403）。
     */
    private void assertUserActive(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_DISABLED);
        }
    }

    /**
     * 绑定前端 SSE 生命周期到上游取消：断连/超时取消 Python 调用并记审计；正常完成静默释放（场景 22/23）。
     */
    private void bindLifecycle(SseEmitter emitter, AtomicReference<RecallUpstreamCall> callRef,
                               Long userId, String requestId) {
        emitter.onError(e -> cancelUpstream(callRef, userId, requestId, true));
        emitter.onTimeout(() -> {
            cancelUpstream(callRef, userId, requestId, true);
            emitter.complete();
        });
        emitter.onCompletion(() -> cancelUpstream(callRef, userId, requestId, false));
    }

    private void cancelUpstream(AtomicReference<RecallUpstreamCall> callRef, Long userId, String requestId,
                                boolean audit) {
        RecallUpstreamCall call = callRef.get();
        if (call != null) {
            call.cancel();
            if (audit) {
                log.info("前端 SSE 断开/超时，已取消 Python 召回 stream, userId={}, requestId={}", userId, requestId);
            }
        }
    }

    private void emitDone(SseEmitter emitter, List<RecallHitDTO> hits) {
        try {
            emitter.send(SseEmitter.event().name("recall_done").data(new RecallDoneEvent(hits)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    private void emitError(SseEmitter emitter, RecallSseError error) {
        try {
            emitter.send(SseEmitter.event().name("error")
                .data(new RecallErrorEvent(error.getCode(), error.getDefaultMessage())));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
