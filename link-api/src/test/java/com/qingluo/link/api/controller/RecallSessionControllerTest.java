package com.qingluo.link.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 召回 session token 签发接口端到端（LINK-104）：鉴权 + 归属校验 + 签发响应，复用真实 resolver/signer 与 H2。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecallSessionControllerTest {

    private static final Long ACTIVE_USER_ID = 99971L;
    private static final Long DISABLED_USER_ID = 99972L;
    private static final Long DS1 = 990001L;
    private static final Long DS2 = 990002L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String activeToken;
    private String disabledToken;

    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        insertUser(ACTIVE_USER_ID, "sessionactive", 1);
        insertUser(DISABLED_USER_ID, "sessiondisabled", 0);
        insertDataset(DS1, ACTIVE_USER_ID, "库1");
        insertDataset(DS2, ACTIVE_USER_ID, "库2");

        StpUtil.login(ACTIVE_USER_ID);
        activeToken = StpUtil.getTokenValue();
        StpUtil.login(DISABLED_USER_ID);
        disabledToken = StpUtil.getTokenValue();
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

    @Test
    @DisplayName("未登录返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(post("/api/v1/recall/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetIds\":[990001]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("datasetIds 为空返回 400")
    void Should_Return400_When_DatasetIdsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/recall/sessions")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetIds\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("datasetIds 越权返回 403")
    void Should_Return403_When_ScopeForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/recall/sessions")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetIds\":[999999]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用户被禁用返回 403")
    void Should_Return403_When_UserDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/recall/sessions")
                .header("satoken", disabledToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetIds\":[990001]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("授权通过返回 token / expires_in / stream_url")
    void Should_ReturnToken_When_ScopeOwned() throws Exception {
        mockMvc.perform(post("/api/v1/recall/sessions")
                .header("satoken", activeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetIds\":[990001,990002]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.expiresIn").value(30))
            .andExpect(jsonPath("$.data.streamUrl").value("http://localhost:8000/api/v1/rag/stream"));
    }
}
