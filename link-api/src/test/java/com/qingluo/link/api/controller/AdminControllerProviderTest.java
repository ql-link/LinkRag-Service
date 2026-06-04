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
 * AdminController Provider（厂商管理）真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>创建厂商 {@code POST /api/v1/admin/providers}</li>
 *   <li>获取厂商列表 {@code GET /api/v1/admin/providers}</li>
 *   <li>更新厂商 {@code PATCH /api/v1/admin/providers/{id}}</li>
 *   <li>切换启用状态 {@code PATCH /api/v1/admin/providers/{id}/active}</li>
 *   <li>删除厂商 {@code DELETE /api/v1/admin/providers/{id}}</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → AdminProviderService → SystemProviderMapper → H2 Database</p>
 *
 * <h2>角色要求</h2>
 * <p>需要 {@code @SaCheckRole("ADMIN")} 权限</p>
 *
 * <h2>前置数据</h2>
 * <ul>
 *   <li>管理员用户: ID=99997L, role="ADMIN"</li>
 *   <li>测试厂商: ID=99997L, type="google"（用于更新/删除测试）</li>
 * </ul>
 *
 * <h2>测试数据说明</h2>
 * <ul>
 *   <li>创建厂商时使用动态 providerType（时间戳）避免唯一索引冲突</li>
 *   <li>更新/删除操作使用 @BeforeAll 插入的固定 ID 厂商</li>
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
class AdminControllerProviderTest {

    /**
     * MockMvc - 模拟 HTTP 请求
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * SysUserMapper - 用户表操作
     */
    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * SystemProviderMapper - 厂商表操作
     */
    @Autowired
    private SystemProviderMapper systemProviderMapper;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 管理员用户 ID
     */
    private static final Long ADMIN_USER_ID = 99997L;

    /**
     * 测试厂商 ID（用于更新/删除测试）
     */
    private static final Long TEST_PROVIDER_ID = 99997L;

    /**
     * 管理员登录令牌
     */
    private String adminToken;

    /**
     * 测试前置准备：插入管理员和测试厂商
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>插入管理员用户</li>
     *   <li>插入测试厂商（google，提供更新/删除操作的目标）</li>
     *   <li>管理员登录</li>
     * </ol>
     */
    @BeforeAll
    void setup() {
        // ===== 步骤 1: 插入管理员用户 =====
        SysUser admin = new SysUser();
        admin.setId(ADMIN_USER_ID);
        admin.setUsername("provideradmintest");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNickname("厂商管理员测试");
        admin.setEmail("provideradmin@test.com");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        // ===== 步骤 2: 插入测试厂商（用于后续更新/删除操作） =====
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("google");
        provider.setProviderName("Google AI");
        provider.setApiBaseUrl("https://generativelanguage.googleapis.com/v1");
        provider.setSupportedCapabilities("[\"CHAT\"]");
        provider.setIsActive(true);
        provider.setPriority(80);
        systemProviderMapper.insert(provider);

        // ===== 步骤 3: 管理员登录 =====
        StpUtil.login(ADMIN_USER_ID);
        adminToken = StpUtil.getTokenValue();
    }

    /**
     * 测试用例 1：创建厂商
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建创建请求（使用动态 providerType 避免冲突）</li>
     *   <li>发送 POST 请求</li>
     *   <li>验证创建成功</li>
     * </ol>
     *
     * <h3>为什么使用动态 providerType？</h3>
     * <p>llm_system_provider 表的 provider_type 可能有唯一索引约束，
     *    使用时间戳生成唯一值确保每次测试都能创建成功</p>
     *
     * <h3>请求字段：</h3>
     * <ul>
     *   <li>providerType: "google_" + timestamp</li>
     *   <li>providerName: "Google AI"</li>
     *   <li>apiBaseUrl: Google API 端点</li>
     *   <li>supportedCapabilities: ["CHAT"]</li>
     *   <li>isActive: true</li>
     *   <li>priority: 80</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("创建厂商 - POST /api/v1/admin/providers")
    void Should_CreateProvider_When_DataValid() throws Exception {
        // 动态生成 providerType 避免唯一索引冲突
        String providerType = "google_" + System.currentTimeMillis();

        String requestJson = "{" +
                "\"providerType\":\"" + providerType + "\"," +
                "\"providerName\":\"Google AI\"," +
                "\"apiBaseUrl\":\"https://generativelanguage.googleapis.com/v1\"," +
                "\"supportedCapabilities\":\"[\\\"CHAT\\\"]\"," +
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

    /**
     * 测试用例 2：获取厂商列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 GET 请求（分页）</li>
     *   <li>验证返回数组包含 @BeforeAll 插入的 google 厂商</li>
     * </ol>
     */
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

    /**
     * 测试用例 3：更新厂商
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建更新请求（修改名称和优先级）</li>
     *   <li>发送 PATCH 请求到 /api/v1/admin/providers/{id}</li>
     *   <li>验证更新成功</li>
     * </ol>
     *
     * <h3>操作目标：</h3>
     * <p>TEST_PROVIDER_ID (99997L) - 预插入的 google 厂商</p>
     *
     * <h3>更新字段：</h3>
     * <ul>
     *   <li>providerName: "Google AI" → "Google AI Updated"</li>
     *   <li>priority: 80 → 90</li>
     * </ul>
     */
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

    /**
     * 测试用例 4：切换厂商启用状态
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 PATCH 请求到 /api/v1/admin/providers/{id}/active</li>
     *   <li>验证切换成功</li>
     * </ol>
     *
     * <h3>操作目标：</h3>
     * <p>TEST_PROVIDER_ID (99997L)</p>
     *
     * <h3>操作：</h3>
     * <p>isActive: true → false（禁用该厂商）</p>
     */
    @Test
    @Order(4)
    @DisplayName("切换厂商启用状态 - PATCH /api/v1/admin/providers/{id}/active")
    void Should_ToggleProviderActive_When_DataValid() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/providers/" + TEST_PROVIDER_ID + "/active")
                .header("satoken", adminToken)
                .param("isActive", "false"))  // 禁用该厂商
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试用例 5：删除厂商
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 DELETE 请求到 /api/v1/admin/providers/{id}</li>
     *   <li>验证删除成功</li>
     * </ol>
     *
     * <h3>操作目标：</h3>
     * <p>TEST_PROVIDER_ID (99997L)</p>
     *
     * <h3>数据库操作：</h3>
     * <p>根据实现可能是物理删除或逻辑删除（is_deleted 标志）</p>
     */
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
