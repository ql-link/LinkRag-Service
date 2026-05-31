package com.qingluo.link.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.core.security.InternalJwtSigner;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.response.RecallHitDTO;
import com.qingluo.link.service.recall.RecallUpstreamCall;
import com.qingluo.link.service.recall.RecallUpstreamClient;
import com.qingluo.link.service.recall.RecallRateLimiter;
import com.qingluo.link.service.recall.RecallUpstreamListener;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 召回网关端到端（acceptance 建流前 HTTP 错误 + 建流后 SSE + 响应头 + 不变量）。
 * @MockBean 上游客户端避免真实调用 Python，@MockBean 限流器避免依赖 RateLimiter 时序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecallControllerTest {

    private static final Long ACTIVE_USER_ID = 88871L;
    private static final Long DISABLED_USER_ID = 88872L;
    private static final Long EMPTY_USER_ID = 88873L;
    private static final Long DS1 = 880001L;
    private static final Long DS2 = 880002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RecallUpstreamClient upstreamClient;

    @MockBean
    private RecallRateLimiter rateLimiter;

    @MockBean
    private InternalJwtSigner jwtSigner;

    private String activeToken;
    private String disabledToken;
    private String emptyToken;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        insertUser(ACTIVE_USER_ID, "recallactive", 1);
        insertUser(DISABLED_USER_ID, "recalldisabled", 0);
        insertUser(EMPTY_USER_ID, "recallempty", 1);
        insertDataset(DS1, ACTIVE_USER_ID, "库1");
        insertDataset(DS2, ACTIVE_USER_ID, "库2");

        StpUtil.login(ACTIVE_USER_ID);
        activeToken = StpUtil.getTokenValue();
        StpUtil.login(DISABLED_USER_ID);
        disabledToken = StpUtil.getTokenValue();
        StpUtil.login(EMPTY_USER_ID);
        emptyToken = StpUtil.getTokenValue();
    }

    @BeforeEach
    void resetMocks() {
        given(rateLimiter.tryAcquire(any())).willReturn(true);
        given(jwtSigner.sign(anyLong(), any(), anyString(), any())).willReturn("test-jwt");
    }

    private void insertUser(Long id, String username, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname(username);
        user.setEmail(username + "@test.com");
        user.setRole("USER");
        user.setStatus(status);
        sysUserMapper.insert(user);
    }

    private void insertDataset(Long id, Long userId, String name) {
        jdbcTemplate.update(
            "INSERT INTO dataset (id, user_id, name, status, is_deleted, deleted_seq, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            id, userId, name);
    }

    private void stubUpstreamDone(List<RecallHitDTO> hits) {
        given(upstreamClient.stream(any(), anyString(), anyString(), any())).willAnswer(invocation -> {
            RecallUpstreamListener listener = invocation.getArgument(3);
            listener.onDone(hits);
            return (RecallUpstreamCall) () -> { };
        });
    }

    // ===== 建流前 HTTP 错误 =====

    @Test
    @DisplayName("未登录返回 401 且不调用 Python")
    void Should_Return401AndNotCallPython_When_NotLoggedIn() throws Exception {
        mockMvc.perform(post("/api/v1/recall/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[880001]}"))
            .andExpect(status().isUnauthorized());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("用户状态非正常返回 403 且不调用 Python")
    void Should_Return403AndNotCallPython_When_UserDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", disabledToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[]}"))
            .andExpect(status().isForbidden());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("datasetIds 越权返回 403 且不调用 Python")
    void Should_Return403AndNotCallPython_When_ScopeForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[999999]}"))
            .andExpect(status().isForbidden());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("query 为空返回 400 且不调用 Python")
    void Should_Return400AndNotCallPython_When_QueryBlank() throws Exception {
        mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"  \",\"datasetIds\":[880001]}"))
            .andExpect(status().isBadRequest());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("传入多余字段返回 400 且不调用 Python")
    void Should_Return400AndNotCallPython_When_UnknownField() throws Exception {
        mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[880001],\"topK\":5}"))
            .andExpect(status().isBadRequest());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("超过限流返回 429 且不调用 Python")
    void Should_Return429AndNotCallPython_When_RateLimited() throws Exception {
        given(rateLimiter.tryAcquire(any())).willReturn(false);
        mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[880001]}"))
            .andExpect(status().isTooManyRequests());
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }

    // ===== 建流后 SSE =====

    @Test
    @DisplayName("Python recall_done 转发为最小候选并设 SSE 响应头")
    void Should_ForwardMinimalHitsWithSseHeaders_When_RecallDone() throws Exception {
        stubUpstreamDone(List.of(new RecallHitDTO("c1", 10L, 1L)));

        MvcResult result = mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[880001,880002]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(header().string("Cache-Control", containsString("no-cache")))
            .andExpect(content().string(containsString("recall_done")))
            .andExpect(content().string(containsString("chunkId")))
            .andExpect(content().string(containsString("c1")))
            .andExpect(content().string(not(containsString("fused_score"))))
            .andExpect(content().string(not(containsString("failed_sources"))));
    }

    @Test
    @DisplayName("Python error 透传为 SSE error 事件")
    void Should_ForwardErrorEvent_When_UpstreamError() throws Exception {
        given(upstreamClient.stream(any(), anyString(), anyString(), any())).willAnswer(invocation -> {
            RecallUpstreamListener listener = invocation.getArgument(3);
            listener.onError(com.qingluo.link.model.enums.RecallSseError.RECALL_ALL_SOURCES_FAILED);
            return (RecallUpstreamCall) () -> { };
        });

        MvcResult result = mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[880001]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:error")))
            .andExpect(content().string(containsString("RECALL_ALL_SOURCES_FAILED")));
    }

    @Test
    @DisplayName("用户无数据集时空 datasetIds 直接返回空 recall_done 且不调用 Python")
    void Should_ReturnEmptyDoneAndNotCallPython_When_NoOwnedDatasets() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/recall/stream")
                .header("satoken", emptyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"q\",\"datasetIds\":[]}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("recall_done")));
        verify(upstreamClient, never()).stream(any(), anyString(), anyString(), any());
    }
}
