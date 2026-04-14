package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 真实集成测试
 * <p>
 * 使用 H2 内存数据库 + 真实 Redis 测试
 * Controller -> Service -> Mapper 完整链路
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 99998L;
    private static final String TEST_USERNAME = "usertest";
    private static final String TEST_PASSWORD = "password123";

    private String token;

    @BeforeAll
    void setup() {
        // 直接在数据库插入测试用户
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("原始昵称");
        user.setEmail("user@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 编程式登录获取 token
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("更新个人资料成功 - PATCH /api/v1/user/profile")
    void Should_UpdateProfile_When_DataValid() throws Exception {
        String requestJson = "{\"nickname\":\"新昵称\",\"email\":\"new@test.com\",\"phone\":\"13800138000\"}";

        mockMvc.perform(patch("/api/v1/user/profile")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 验证更新后的数据
        mockMvc.perform(get("/api/v1/user/profile")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.nickname").value("新昵称"))
            .andExpect(jsonPath("$.data.email").value("new@test.com"))
            .andExpect(jsonPath("$.data.phone").value("13800138000"));
    }

    @Test
    @Order(2)
    @DisplayName("获取个人资料成功 - GET /api/v1/user/profile")
    void Should_ReturnUserProfile_When_GetProfileSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
            .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @Order(3)
    @DisplayName("未登录访问应返回 401 - GET /api/v1/user/profile")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
            .andExpect(status().isUnauthorized());
    }
}