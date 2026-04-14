package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController 真实集成测试
 * <p>
 * 使用 H2 内存数据库 + 真实 Redis 测试
 * Controller -> Service -> Mapper 完整链路
 * 需要 ADMIN 角色
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SystemProviderMapper systemProviderMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long ADMIN_USER_ID = 99995L;
    private static final Long NORMAL_USER_ID = 99996L;
    private static final Long TEST_PROVIDER_ID = 99995L;

    private String adminToken;

    @BeforeAll
    void setup() {
        // 插入管理员用户
        SysUser admin = new SysUser();
        admin.setId(ADMIN_USER_ID);
        admin.setUsername("admintest");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNickname("管理员测试");
        admin.setEmail("admin@test.com");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        // 插入普通用户
        SysUser normalUser = new SysUser();
        normalUser.setId(NORMAL_USER_ID);
        normalUser.setUsername("normaltest");
        normalUser.setPasswordHash(passwordEncoder.encode("user123"));
        normalUser.setNickname("普通用户测试");
        normalUser.setEmail("normal@test.com");
        normalUser.setRole("USER");
        normalUser.setStatus(1);
        sysUserMapper.insert(normalUser);

        // 插入 SystemProvider
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("anthropic");
        provider.setProviderName("Anthropic");
        provider.setApiBaseUrl("https://api.anthropic.com/v1");
        provider.setSupportedModels("[\"claude-3\", \"claude-2\"]");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        // 编程式登录获取 token
        StpUtil.login(ADMIN_USER_ID);
        adminToken = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("管理员获取用户列表 - GET /api/v1/admin/users")
    void Should_ReturnUserList_When_AdminListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .header("satoken", adminToken)
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("管理员修改用户状态 - PATCH /api/v1/admin/users/{id}/status")
    void Should_UpdateUserStatus_When_AdminUpdate() throws Exception {
        String requestJson = "{\"status\":0}";

        mockMvc.perform(patch("/api/v1/admin/users/" + NORMAL_USER_ID + "/status")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    @DisplayName("管理员修改用户角色 - PATCH /api/v1/admin/users/{id}/role")
    void Should_UpdateUserRole_When_AdminUpdate() throws Exception {
        String requestJson = "{\"role\":\"ADMIN\"}";

        mockMvc.perform(patch("/api/v1/admin/users/" + NORMAL_USER_ID + "/role")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("管理员获取厂商列表 - GET /api/v1/admin/providers")
    void Should_ReturnProviderList_When_AdminListProviders() throws Exception {
        mockMvc.perform(get("/api/v1/admin/providers")
                .header("satoken", adminToken)
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    // 注意：由于 TestSecurityConfig 禁用了所有安全检查（anyRequest().permitAll()），
    // 无法在此测试中验证 401/403 响应。安全机制已在 AuthControllerTest 等其他测试中验证。
}