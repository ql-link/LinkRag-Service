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
 * AdminController Provider 真实集成测试
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
class AdminControllerProviderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SystemProviderMapper systemProviderMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long ADMIN_USER_ID = 99997L;
    private static final Long TEST_PROVIDER_ID = 99997L;
    private String adminToken;

    @BeforeAll
    void setup() {
        // 插入管理员用户
        SysUser admin = new SysUser();
        admin.setId(ADMIN_USER_ID);
        admin.setUsername("provideradmintest");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNickname("厂商管理员测试");
        admin.setEmail("provideradmin@test.com");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        // 插入 SystemProvider
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("google");
        provider.setProviderName("Google AI");
        provider.setApiBaseUrl("https://generativelanguage.googleapis.com/v1");
        provider.setSupportedModels("[\"gemini-pro\", \"gemini-ultra\"]");
        provider.setIsActive(true);
        provider.setPriority(80);
        systemProviderMapper.insert(provider);

        // 编程式登录获取 token
        StpUtil.login(ADMIN_USER_ID);
        adminToken = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("创建厂商 - POST /api/v1/admin/providers")
    void Should_CreateProvider_When_DataValid() throws Exception {
        String providerType = "google_" + System.currentTimeMillis();
        String requestJson = "{" +
                "\"providerType\":\"" + providerType + "\"," +
                "\"providerName\":\"Google AI\"," +
                "\"apiBaseUrl\":\"https://generativelanguage.googleapis.com/v1\"," +
                "\"supportedModels\":\"[\\\"gemini-pro\\\", \\\"gemini-ultra\\\"]\"," +
                "\"isActive\":true," +
                "\"priority\":80" +
                "}";

        mockMvc.perform(post("/api/v1/admin/providers")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("获取厂商列表 - GET /api/v1/admin/providers")
    void Should_ReturnProviderList_When_ListProviders() throws Exception {
        mockMvc.perform(get("/api/v1/admin/providers")
                .header("satoken", adminToken)
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @Order(3)
    @DisplayName("更新厂商 - PATCH /api/v1/admin/providers/{id}")
    void Should_UpdateProvider_When_DataValid() throws Exception {
        String requestJson = "{\"providerName\":\"Google AI Updated\",\"priority\":90}";

        mockMvc.perform(patch("/api/v1/admin/providers/" + TEST_PROVIDER_ID)
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("切换厂商启用状态 - PATCH /api/v1/admin/providers/{id}/active")
    void Should_ToggleProviderActive_When_DataValid() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/providers/" + TEST_PROVIDER_ID + "/active")
                .header("satoken", adminToken)
                .param("isActive", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("删除厂商 - DELETE /api/v1/admin/providers/{id}")
    void Should_DeleteProvider_When_DataValid() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/providers/" + TEST_PROVIDER_ID)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}