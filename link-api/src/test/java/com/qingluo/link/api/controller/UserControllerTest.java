package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>获取个人资料 {@code GET /api/v1/user/profile}</li>
 *   <li>更新个人资料 {@code PATCH /api/v1/user/profile}</li>
 *   <li>未登录访问校验</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → AuthService → SysUserMapper → H2 Database</p>
 *
 * <h2>测试数据</h2>
 * <ul>
 *   <li>测试用户 ID: {@code 99998L}</li>
 *   <li>用户名: {@code usertest}</li>
 *   <li>初始昵称: {@code "原始昵称"}</li>
 *   <li>更新后昵称: {@code "新昵称"}</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2026-04-14
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTest {

    /**
     * MockMvc - 模拟 HTTP 请求
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * ObjectMapper - JSON 序列化/反序列化
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * SysUserMapper - 直接操作数据库
     */
    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 测试用户 ID
     */
    private static final Long TEST_USER_ID = 99998L;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "usertest";

    /**
     * 测试密码
     */
    private static final String TEST_PASSWORD = "password123";

    /**
     * 登录后的访问令牌
     */
    private String token;

    /**
     * 测试前置准备：插入测试用户并登录
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>创建用户实体（昵称为"原始昵称"，用于后续更新测试）</li>
     *   <li>BCrypt 加密密码</li>
     *   <li>通过 SysUserMapper 插入数据库</li>
     *   <li>StpUtil 编程式登录</li>
     * </ol>
     */
    @BeforeAll
    void setup() {
        // 创建测试用户
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("原始昵称");      // 初始昵称
        user.setEmail("user@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 编程式登录
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @BeforeEach
    void setupUserCacheMock() {
        given(userCacheService.getOrLoad(anyLong(), any())).willAnswer(this::loadFromSupplier);
    }

    private Object loadFromSupplier(InvocationOnMock invocation) {
        Supplier<?> loader = invocation.getArgument(1);
        return loader.get();
    }

    /**
     * 测试用例 1：更新个人资料成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建更新请求（昵称、邮箱、手机号）</li>
     *   <li>发送 PATCH 请求更新资料</li>
     *   <li>再次发送 GET 请求验证更新后的数据</li>
     * </ol>
     *
     * <h3>更新字段：</h3>
     * <ul>
     *   <li>nickname: "原始昵称" → "新昵称"</li>
     *   <li>email: "user@test.com" → "new@test.com"</li>
     *   <li>phone: null → "13800138000"</li>
     * </ul>
     *
     * <h3>数据库操作：</h3>
     * <ul>
     *   <li>UPDATE sys_user SET nickname=?, email=?, phone=? WHERE id=?</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("更新个人资料成功 - PATCH /api/v1/user/profile")
    void Should_UpdateProfile_When_DataValid() throws Exception {
        // ===== 步骤 1: 发送更新请求 =====
        String requestJson = "{\"nickname\":\"新昵称\",\"email\":\"new@test.com\",\"phone\":\"13800138000\"}";

        mockMvc.perform(patch("/api/v1/user/profile")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // ===== 步骤 2: 验证更新后的数据（再次查询） =====
        mockMvc.perform(get("/api/v1/user/profile")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            // 验证昵称已更新
            .andExpect(jsonPath("$.data.nickname").value("新昵称"))
            // 验证邮箱已更新
            .andExpect(jsonPath("$.data.email").value("new@test.com"))
            // 验证手机号已更新
            .andExpect(jsonPath("$.data.phone").value("13800138000"));
    }

    /**
     * 测试用例 2：获取个人资料成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>携带 token 发送 GET 请求</li>
     *   <li>验证返回的用户信息正确</li>
     * </ol>
     *
     * <h3>验证点：</h3>
     * <ul>
     *   <li>username = "usertest"</li>
     *   <li>role = "USER"</li>
     * </ul>
     */
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

    /**
     * 测试用例 3：未登录访问应返回 401
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>不携带 token 发送 GET 请求</li>
     *   <li>验证返回 401 Unauthorized</li>
     * </ol>
     *
     * <h3>验证点：</h3>
     * <p>验证 @SaCheckLogin 注解的认证拦截生效</p>
     */
    @Test
    @Order(3)
    @DisplayName("未登录访问应返回 401 - GET /api/v1/user/profile")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
            .andExpect(status().isUnauthorized());
    }
}