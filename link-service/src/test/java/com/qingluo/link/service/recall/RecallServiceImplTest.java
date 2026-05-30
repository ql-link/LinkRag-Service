package com.qingluo.link.service.recall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.security.InternalJwtSigner;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.RecallStreamRequest;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.RecallProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 召回编排建流前逻辑与内部调用自洽（acceptance 场景 2/3/6/8/10/11/12/13/15/26/27）。
 * 建流后的 SSE 字节内容在 RecallControllerTest 用 asyncDispatch 验证。
 */
@ExtendWith(MockitoExtension.class)
class RecallServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private RecallScopeResolver scopeResolver;
    @Mock private RecallRateLimiter rateLimiter;
    @Mock private RecallUpstreamClient upstreamClient;
    @Mock private InternalJwtSigner jwtSigner;

    private RecallServiceImpl service;

    @BeforeEach
    void init() {
        service = new RecallServiceImpl(sysUserMapper, scopeResolver, rateLimiter, upstreamClient, jwtSigner,
            new RecallProperties());
    }

    private RecallStreamRequest req(List<Long> datasetIds) {
        RecallStreamRequest request = new RecallStreamRequest();
        request.setQuery("什么是 RAG");
        request.setDatasetIds(datasetIds);
        return request;
    }

    private SysUser user(int status) {
        SysUser user = new SysUser();
        user.setId(100L);
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("Should_RejectAndNotCallPython_When_UserDisabled")
    void Should_RejectAndNotCallPython_When_UserDisabled() {
        given(sysUserMapper.selectById(100L)).willReturn(user(0));

        assertThatThrownBy(() -> service.recall(100L, req(List.of(1L))))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(rateLimiter, scopeResolver, upstreamClient, jwtSigner);
    }

    @Test
    @DisplayName("Should_RejectAndNotCallPython_When_RateLimited")
    void Should_RejectAndNotCallPython_When_RateLimited() {
        given(sysUserMapper.selectById(100L)).willReturn(user(1));
        given(rateLimiter.tryAcquire(100L)).willReturn(false);

        assertThatThrownBy(() -> service.recall(100L, req(List.of(1L))))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(scopeResolver, upstreamClient, jwtSigner);
    }

    @Test
    @DisplayName("Should_PropagateForbiddenAndNotCallPython_When_ScopeForbidden")
    void Should_PropagateForbiddenAndNotCallPython_When_ScopeForbidden() {
        given(sysUserMapper.selectById(100L)).willReturn(user(1));
        given(rateLimiter.tryAcquire(100L)).willReturn(true);
        given(scopeResolver.resolve(eq(100L), any()))
            .willThrow(new BusinessException(ErrorCode.RECALL_SCOPE_FORBIDDEN));

        assertThatThrownBy(() -> service.recall(100L, req(List.of(1L, 2L))))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(upstreamClient, jwtSigner);
    }

    @Test
    @DisplayName("Should_ReturnEmptyDoneAndNotCallPython_When_NoOwnedDatasets")
    void Should_ReturnEmptyDoneAndNotCallPython_When_NoOwnedDatasets() {
        given(sysUserMapper.selectById(100L)).willReturn(user(1));
        given(rateLimiter.tryAcquire(100L)).willReturn(true);
        given(scopeResolver.resolve(eq(100L), any())).willReturn(ResolvedScope.empty());

        SseEmitter emitter = service.recall(100L, req(List.of()));

        assertThat(emitter).isNotNull();
        verifyNoInteractions(upstreamClient, jwtSigner);
    }

    @Test
    @DisplayName("Should_CallPythonWithSelfConsistentBodyAndJwt_When_Valid")
    void Should_CallPythonWithSelfConsistentBodyAndJwt_When_Valid() {
        given(sysUserMapper.selectById(100L)).willReturn(user(1));
        given(rateLimiter.tryAcquire(100L)).willReturn(true);
        given(scopeResolver.resolve(eq(100L), any())).willReturn(ResolvedScope.of(List.of(1L, 2L)));
        given(jwtSigner.sign(eq(100L), eq(List.of(1L, 2L)), anyString(), any(Instant.class)))
            .willReturn("jwt-token");
        given(upstreamClient.stream(any(), anyString(), anyString(), any())).willReturn(() -> { });

        service.recall(100L, req(List.of(1L, 2L)));

        ArgumentCaptor<RecallUpstreamRequest> upCaptor = ArgumentCaptor.forClass(RecallUpstreamRequest.class);
        ArgumentCaptor<String> jwtCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(upstreamClient).stream(upCaptor.capture(), jwtCaptor.capture(), requestIdCaptor.capture(), any());

        RecallUpstreamRequest upstream = upCaptor.getValue();
        assertThat(upstream.getUserId()).isEqualTo(100L);
        assertThat(upstream.getDatasetIds()).containsExactly(1L, 2L);
        assertThat(upstream.getQuery()).isEqualTo("什么是 RAG");
        assertThat(jwtCaptor.getValue()).isEqualTo("jwt-token");

        // body 与 JWT 同源：sign 用的 userId/datasetIds 与 body 相同，requestId 即 jti（场景 12/13/15）
        verify(jwtSigner).sign(eq(100L), eq(List.of(1L, 2L)), eq(requestIdCaptor.getValue()), any(Instant.class));
    }

    @Test
    @DisplayName("Should_SetEmitterTimeoutLongerThanUpstreamTimeout_When_Valid")
    void Should_SetEmitterTimeoutLongerThanUpstreamTimeout_When_Valid() {
        given(sysUserMapper.selectById(100L)).willReturn(user(1));
        given(rateLimiter.tryAcquire(100L)).willReturn(true);
        given(scopeResolver.resolve(eq(100L), any())).willReturn(ResolvedScope.of(List.of(1L)));
        given(jwtSigner.sign(eq(100L), any(), anyString(), any(Instant.class))).willReturn("jwt");
        given(upstreamClient.stream(any(), anyString(), anyString(), any())).willReturn(() -> { });

        RecallProperties props = new RecallProperties();
        SseEmitter emitter = service.recall(100L, req(List.of(1L)));

        // emitter 超时严格大于上游整体超时（okhttp callTimeout = stream-timeout-ms），
        // 保证超时由上游先触发 RECALL_TIMEOUT，而非前端 SSE 先超时被静默关闭（R1：避免等长计时器竞争）。
        assertThat(emitter.getTimeout())
            .isEqualTo(props.getStreamTimeoutMs() + props.getEmitterTimeoutBufferMs());
        assertThat(emitter.getTimeout()).isGreaterThan(props.getStreamTimeoutMs());
    }
}
