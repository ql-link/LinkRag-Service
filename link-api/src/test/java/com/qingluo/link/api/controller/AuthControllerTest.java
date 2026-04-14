package com.qingluo.link.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 真实集成测试
 *
 * 测试链路：Controller -> Service -> Mapper -> H2 Database + Redis
 * 完全真实的测试环境，包括真实的 Redis 缓存
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "authtest_" + System.currentTimeMillis();
    private static final String TEST_PASSWORD = "password123";

    @Test
    @Order(1)
    @DisplayName("注册成功 - POST /api/v1/auth/register")
    void Should_ReturnAuthResult_When_RegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setNickname("测试用户");
        request.setEmail("auth@test.com");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @Order(2)
    @DisplayName("登录成功 - POST /api/v1/auth/login")
    void Should_ReturnAuthResult_When_LoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @Order(3)
    @DisplayName("登出成功 - POST /api/v1/auth/logout")
    void Should_ReturnOk_When_LogoutSuccess() throws Exception {
        // 先登录获取 token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(TEST_USERNAME);
        loginRequest.setPassword(TEST_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String logoutToken = loginJson.get("data").get("accessToken").asText();

        // 调用登出
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("satoken", logoutToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("登录失败（密码错误） - POST /api/v1/auth/login")
    void Should_ReturnUnauthorized_When_LoginFailed() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
}
