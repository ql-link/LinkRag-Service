package com.qingluo.link.service.recall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.security.RecallSessionJwtSigner;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.RecallSessionRequest;
import com.qingluo.link.model.dto.response.RecallSessionResponse;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.RecallProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * session token 签发编排：用户状态校验 + 归属校验复用 + 签发 + streamUrl 组装。
 */
@ExtendWith(MockitoExtension.class)
class RecallSessionServiceImplTest {

    private static final Long USER_ID = 123L;

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private RecallScopeResolver scopeResolver;
    @Mock
    private RecallSessionJwtSigner sessionJwtSigner;
    @Mock
    private RecallProperties properties;

    @InjectMocks
    private RecallSessionServiceImpl service;

    private RecallSessionRequest request;

    @BeforeEach
    void setUp() {
        request = new RecallSessionRequest();
        request.setDatasetIds(List.of(1L, 2L));
    }

    private SysUser activeUser() {
        SysUser u = new SysUser();
        u.setId(USER_ID);
        u.setStatus(1);
        return u;
    }

    @Test
    @DisplayName("Should_IssueTokenWithStreamUrl_When_UserActiveAndScopeOwned")
    void Should_IssueTokenWithStreamUrl_When_UserActiveAndScopeOwned() {
        given(sysUserMapper.selectById(USER_ID)).willReturn(activeUser());
        given(scopeResolver.resolve(eq(USER_ID), any())).willReturn(ResolvedScope.of(List.of(1L, 2L)));
        given(sessionJwtSigner.sign(anyLong(), any(), any(Instant.class))).willReturn("signed-token");
        given(properties.getSessionJwtExpSeconds()).willReturn(30L);
        given(properties.getSessionStreamBaseUrl()).willReturn("https://rag.example.com/");

        RecallSessionResponse resp = service.issue(USER_ID, request);

        assertThat(resp.getToken()).isEqualTo("signed-token");
        assertThat(resp.getExpiresIn()).isEqualTo(30L);
        // 末尾斜杠被规整，拼接 /api/v1/recall/stream。
        assertThat(resp.getStreamUrl()).isEqualTo("https://rag.example.com/api/v1/recall/stream");
        verify(sessionJwtSigner).sign(eq(USER_ID), eq(List.of(1L, 2L)), any(Instant.class));
    }

    @Test
    @DisplayName("Should_ThrowAuthDisabledAndNotSign_When_UserDisabled")
    void Should_ThrowAuthDisabledAndNotSign_When_UserDisabled() {
        SysUser disabled = activeUser();
        disabled.setStatus(0);
        given(sysUserMapper.selectById(USER_ID)).willReturn(disabled);

        assertThatThrownBy(() -> service.issue(USER_ID, request))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.AUTH_DISABLED.getCode());

        verify(sessionJwtSigner, never()).sign(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Should_PropagateScopeForbiddenAndNotSign_When_DatasetNotOwned")
    void Should_PropagateScopeForbiddenAndNotSign_When_DatasetNotOwned() {
        given(sysUserMapper.selectById(USER_ID)).willReturn(activeUser());
        given(scopeResolver.resolve(eq(USER_ID), any()))
            .willThrow(new BusinessException(ErrorCode.RECALL_SCOPE_FORBIDDEN));

        assertThatThrownBy(() -> service.issue(USER_ID, request))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.RECALL_SCOPE_FORBIDDEN.getCode());

        verify(sessionJwtSigner, never()).sign(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Should_ThrowAuthDisabledAndNotSign_When_UserIdNonPositive")
    void Should_ThrowAuthDisabledAndNotSign_When_UserIdNonPositive() {
        assertThatThrownBy(() -> service.issue(0L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo(ErrorCode.AUTH_DISABLED.getCode());

        verify(sysUserMapper, never()).selectById(any());
        verify(sessionJwtSigner, never()).sign(anyLong(), any(), any());
    }
}
