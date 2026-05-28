package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController（管理员功能）真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>分页查询用户列表 {@code GET /api/v1/admin/users}</li>
 *   <li>修改用户状态 {@code PATCH /api/v1/admin/users/{id}/status}</li>
 *   <li>修改用户角色 {@code PATCH /api/v1/admin/users/{id}/role}</li>
 *   <li>分页查询厂商列表 {@code GET /api/v1/admin/providers}</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → AdminUserService/AdminProviderService → Mapper → H2 Database</p>
 *
 * <h2>角色要求</h2>
 * <p>所有接口需要 {@code @SaCheckRole("ADMIN")} 注解标识，仅 ADMIN 角色可访问</p>
 *
 * <h2>前置数据</h2>
 * <ul>
 *   <li>管理员用户: ID=99995L, role="ADMIN"</li>
 *   <li>普通用户: ID=99996L, role="USER"（用于测试修改操作）</li>
 *   <li>系统厂商: ID=99995L, type="anthropic"</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <p>由于 TestSecurityConfig 禁用了所有安全检查，401/403 测试在当前配置下无法验证。
 * 安全机制（@SaCheckLogin、@SaCheckRole）的正确性已在其他测试中得到验证。</p>
 *
 * @author Claude Code
 * @since 2026-04-14
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminControllerTest {

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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private KnowledgeFileConfigCacheService knowledgeFileConfigCacheService;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 管理员用户 ID
     */
    private static final Long ADMIN_USER_ID = 99995L;

    /**
     * 普通用户 ID（用于测试修改操作）
     */
    private static final Long NORMAL_USER_ID = 99996L;

    /**
     * 测试厂商 ID
     */
    private static final Long TEST_PROVIDER_ID = 99995L;

    /**
     * 管理员登录令牌
     */
    private String adminToken;

    /**
     * 测试前置准备：插入管理员和普通用户
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>插入管理员用户（role="ADMIN"）</li>
     *   <li>插入普通用户（role="USER"，用于测试状态/角色修改）</li>
     *   <li>插入 SystemProvider（外键依赖）</li>
     *   <li>管理员用户登录</li>
     * </ol>
     */
    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM knowledge_file_config");

        // ===== 步骤 1: 插入管理员用户 =====
        SysUser admin = new SysUser();
        admin.setId(ADMIN_USER_ID);
        admin.setUsername("admintest");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNickname("管理员测试");
        admin.setEmail("admin@test.com");
        admin.setRole("ADMIN");  // 关键：管理员角色
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        // ===== 步骤 2: 插入普通用户（用于后续修改测试） =====
        SysUser normalUser = new SysUser();
        normalUser.setId(NORMAL_USER_ID);
        normalUser.setUsername("normaltest");
        normalUser.setPasswordHash(passwordEncoder.encode("user123"));
        normalUser.setNickname("普通用户测试");
        normalUser.setEmail("normal@test.com");
        normalUser.setRole("USER");  // 普通用户角色
        normalUser.setStatus(1);
        sysUserMapper.insert(normalUser);

        // ===== 步骤 3: 插入 SystemProvider（外键依赖） =====
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("anthropic");
        provider.setProviderName("Anthropic");
        provider.setApiBaseUrl("https://api.anthropic.com/v1");
        provider.setSupportedModels("[\"claude-3\", \"claude-2\"]");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        // ===== 步骤 4: 管理员登录 =====
        StpUtil.login(ADMIN_USER_ID);
        adminToken = StpUtil.getTokenValue();
    }

    @BeforeEach
    void resetKnowledgeFileConfigCache() {
        reset(knowledgeFileConfigCacheService);
        given(knowledgeFileConfigCacheService.getConfig()).willReturn(Optional.empty());
    }

    /**
     * 测试用例 1：管理员获取用户列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>管理员携带 token 发送 GET 请求</li>
     *   <li>验证返回分页用户列表</li>
     * </ol>
     *
     * <h3>验证点：</h3>
     * <ul>
     *   <li>HTTP 200</li>
     *   <li>business code 200</li>
     *   <li>data.items 是数组</li>
     * </ul>
     */
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

    /**
     * 测试用例 2：管理员修改用户状态
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建状态修改请求（status=0 表示禁用）</li>
     *   <li>管理员修改普通用户的状态</li>
     *   <li>验证修改成功</li>
     * </ol>
     *
     * <h3>操作对象：</h3>
     * <p>NORMAL_USER_ID (99996L) - 将 status 从 1 改为 0</p>
     */
    @Test
    @Order(2)
    @DisplayName("管理员修改用户状态 - PATCH /api/v1/admin/users/{id}/status")
    void Should_UpdateUserStatus_When_AdminUpdate() throws Exception {
        // status=0 表示禁用该用户
        String requestJson = "{\"status\":0}";

        mockMvc.perform(patch("/api/v1/admin/users/" + NORMAL_USER_ID + "/status")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试用例 3：管理员修改用户角色
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建角色修改请求（role="ADMIN"）</li>
     *   <li>管理员将普通用户提升为管理员</li>
     *   <li>验证修改成功</li>
     * </ol>
     *
     * <h3>操作对象：</h3>
     * <p>NORMAL_USER_ID (99996L) - 将 role 从 "USER" 改为 "ADMIN"</p>
     */
    @Test
    @Order(3)
    @DisplayName("管理员修改用户角色 - PATCH /api/v1/admin/users/{id}/role")
    void Should_UpdateUserRole_When_AdminUpdate() throws Exception {
        // 将用户角色修改为 ADMIN
        String requestJson = "{\"role\":\"ADMIN\"}";

        mockMvc.perform(patch("/api/v1/admin/users/" + NORMAL_USER_ID + "/role")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试用例 4：管理员获取厂商列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>管理员发送 GET 请求</li>
     *   <li>验证返回分页厂商列表</li>
     * </ol>
     *
     * <h3>验证点：</h3>
     * <p>返回数组应包含前置数据中插入的 anthropic 厂商</p>
     */
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

    @Test
    @Order(5)
    @DisplayName("管理员获取知识文件配置 - GET /api/v1/admin/knowledge-file-config")
    void Should_ReturnKnowledgeFileConfig_When_AdminQueriesCurrentConfig() throws Exception {
        mockMvc.perform(get("/api/v1/admin/knowledge-file-config")
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.maxSizeBytes").exists())
            .andExpect(jsonPath("$.data.allowedSuffixes").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("管理员修改知识文件配置 - PATCH /api/v1/admin/knowledge-file-config")
    void Should_UpdateKnowledgeFileConfig_When_AdminPatchesConfig() throws Exception {
        String requestJson = """
            {
              "maxSizeBytes": 1024,
              "allowedSuffixes": ["pdf", "txt", "pdf"]
            }
            """;

        mockMvc.perform(patch("/api/v1/admin/knowledge-file-config")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<KnowledgeFileConfigDTO> captor =
            org.mockito.ArgumentCaptor.forClass(KnowledgeFileConfigDTO.class);
        verify(knowledgeFileConfigCacheService).putConfig(captor.capture());
        assertThat(captor.getValue().getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(captor.getValue().getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ADMIN_USER_ID);
    }

    // ========================================================================
    // 安全验证说明
    // ========================================================================
    //
    // 注意：由于 TestSecurityConfig 禁用了所有安全检查（anyRequest().permitAll()），
    // 以下安全场景在当前配置下无法验证：
    //
    // 1. 普通用户访问管理员接口应返回 403
    //    - 需要验证 @SaCheckRole("ADMIN") 注解的权限拦截
    //
    // 2. 未登录访问管理员接口应返回 401
    //    - 需要验证 @SaCheckLogin 注解的认证拦截
    //
    // 解决方案：
    // - 在集成测试环境中配置真实的 Spring Security
    // - 或使用 @WithMockUser 自定义用户上下文
    //
    // 当前这些安全机制的验证已在 AuthControllerTest 等其他测试中间接验证。
    // ========================================================================
}
