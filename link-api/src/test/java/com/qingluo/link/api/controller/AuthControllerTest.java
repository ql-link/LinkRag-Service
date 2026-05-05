package com.qingluo.link.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.qingluo.link.service.cache.UserCacheService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>用户注册功能 {@code POST /api/v1/auth/register}</li>
 *   <li>用户登录功能 {@code POST /api/v1/auth/login}</li>
 *   <li>用户登出功能 {@code POST /api/v1/auth/logout}</li>
 *   <li>登录失败场景（密码错误）</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → Service → Mapper → H2 Database</p>
 * <p>注：Redis 缓存由 sa-token 使用，测试中使用真实的本地 Redis</p>
 *
 * <h2>测试数据</h2>
 * <p>测试用户名使用时间戳确保唯一性：{@code authtest_ + timestamp}</p>
 *
 * @author Claude Code
 * @since 2026-04-14
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    /**
     * MockMvc - 用于模拟 HTTP 请求
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * ObjectMapper - 用于 JSON 序列化/反序列化
     */
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @MockBean
    private UserCacheService userCacheService;

    /**
     * 测试用户名 - 使用时间戳保证唯一，避免与数据库已有数据冲突
     */
    private static final String TEST_USERNAME = "authtest_" + System.currentTimeMillis();

    /**
     * 测试密码 - 符合系统密码复杂度要求
     */
    private static final String TEST_PASSWORD = "password123";

    /**
     * 测试用例 1：用户注册成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建注册请求（用户名、密码、邮箱）</li>
     *   <li>发送 POST 请求到 /api/v1/auth/register</li>
     *   <li>验证响应状态码为 200 OK</li>
     *   <li>验证响应 code 为 200</li>
     *   <li>验证返回了 accessToken</li>
     *   <li>验证 tokenType 为 "Bearer"</li>
     * </ol>
     *
     * <h3>预期结果：</h3>
     * <ul>
     *   <li>注册成功，返回 JWT 访问令牌</li>
     *   <li>用户信息写入 H2 数据库 sys_user 表</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("注册成功 - POST /api/v1/auth/register")
    void Should_ReturnAuthResult_When_RegisterSuccess() throws Exception {
        // Step 1: 构建注册请求
        RegisterRequest request = new RegisterRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setEmail("auth@test.com");

        // Step 2: 发送注册请求，验证响应
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // 验证 HTTP 状态码为 200 OK
            .andExpect(status().isOk())
            // 验证业务响应码为 200
            .andExpect(jsonPath("$.code").value(200))
            // 验证返回了 accessToken（JWT 令牌）
            .andExpect(jsonPath("$.data.accessToken").exists())
            // 验证令牌类型为 Bearer（OAuth2 标准）
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        SysUser createdUser = sysUserMapper.selectByUsername(TEST_USERNAME);
        Assertions.assertNotNull(createdUser);
        Assertions.assertTrue(createdUser.getNickname().startsWith("用户"));
    }

    /**
     * 测试用例 2：用户登录成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>使用注册阶段创建的用户凭证构建登录请求</li>
     *   <li>发送 POST 请求到 /api/v1/auth/login</li>
     *   <li>验证响应包含有效的访问令牌</li>
     * </ol>
     *
     * <h3>预期结果：</h3>
     * <ul>
     *   <li>登录成功，返回新的访问令牌</li>
     *   <li>Redis 中存储了会话信息（sa-token）</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("登录成功 - POST /api/v1/auth/login")
    void Should_ReturnAuthResult_When_LoginSuccess() throws Exception {
        // Step 1: 构建登录请求（使用注册阶段的用户名和密码）
        LoginRequest request = new LoginRequest();
        request.setAccount(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        // Step 2: 发送登录请求
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            // 验证返回了 accessToken
            .andExpect(jsonPath("$.data.accessToken").exists())
            // 验证令牌类型
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    /**
     * 测试用例 3：用户登出成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>先调用登录接口获取有效的访问令牌</li>
     *   <li>从登录响应中提取 accessToken</li>
     *   <li>携带 token 调用 POST /api/v1/auth/logout</li>
     *   <li>验证登出成功（状态码 200）</li>
     * </ol>
     *
     * <h3>预期结果：</h3>
     * <ul>
     *   <li>登出成功，Redis 中的会话信息被清除</li>
     *   <li>后续使用该 token 的请求应该失败（401）</li>
     * </ul>
     */
    @Test
    @Order(3)
    @DisplayName("邮箱登录成功 - POST /api/v1/auth/login")
    void Should_ReturnAuthResult_When_LoginWithEmailSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("auth@test.com");
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    /**
     * 测试用例 4：用户登出成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>先调用登录接口获取有效的访问令牌</li>
     *   <li>从登录响应中提取 accessToken</li>
     *   <li>携带 token 调用 POST /api/v1/auth/logout</li>
     *   <li>验证登出成功（状态码 200）</li>
     * </ol>
     */
    @Test
    @Order(4)
    @DisplayName("登出成功 - POST /api/v1/auth/logout")
    void Should_ReturnOk_When_LogoutSuccess() throws Exception {
        // ===== 阶段 1: 登录获取 token =====
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setAccount(TEST_USERNAME);
        loginRequest.setPassword(TEST_PASSWORD);

        // 执行登录请求并获取响应
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        // 从 JSON 响应中提取 accessToken
        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String logoutToken = loginJson.get("data").get("accessToken").asText();

        // ===== 阶段 2: 使用 token 登出 =====
        // 携带 satoken header 调用登出接口
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("satoken", logoutToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试用例 5：登录失败（密码错误）
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>使用正确的用户名 + 错误的密码构建登录请求</li>
     *   <li>发送 POST 请求到 /api/v1/auth/login</li>
     *   <li>验证响应状态码为 401 Unauthorized</li>
     * </ol>
     *
     * <h3>预期结果：</h3>
     * <ul>
     *   <li>登录失败，返回 401 状态码</li>
     *   <li>不返回任何访问令牌</li>
     * </ul>
     */
    @Test
    @Order(5)
    @DisplayName("登录失败（密码错误） - POST /api/v1/auth/login")
    void Should_ReturnUnauthorized_When_LoginFailed() throws Exception {
        // 构建登录请求：正确的用户名 + 错误的密码
        LoginRequest request = new LoginRequest();
        request.setAccount(TEST_USERNAME);
        request.setPassword("wrongpassword"); // 故意使用错误密码

        // 发送登录请求，验证返回 401 未授权
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Spring Security 返回 401 Unauthorized
            .andExpect(status().isUnauthorized());
    }
}
