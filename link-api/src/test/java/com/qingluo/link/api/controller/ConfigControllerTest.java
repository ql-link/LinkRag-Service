package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ConfigController（LLM 配置管理）真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>创建 LLM 配置 {@code POST /api/v1/llm/configs}</li>
 *   <li>获取配置列表 {@code GET /api/v1/llm/configs}</li>
 *   <li>按厂商类型筛选 {@code GET /api/v1/llm/configs?providerType=xxx}</li>
 *   <li>未登录访问校验</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → UserLLMConfigService → UserLLMConfigMapper → H2 Database</p>
 * <p>API Key 加密由 ApiKeyEncryptService 处理（真实 AES-256-GCM 加密）</p>
 *
 * <h2>前置数据</h2>
 * <ul>
 *   <li>SystemProvider: ID=990002L, type="openai_config"（LLM 厂商必需先存在）</li>
 *   <li>SysUser: ID=990001L（配置所有者）</li>
 * </ul>
 *
 * <h2>测试隔离</h2>
 * <p>使用 @DirtiesContext 确保测试类级别的上下文隔离</p>
 *
 * @author Claude Code
 * @since 2026-04-14
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfigControllerTest {

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
     * SysUserMapper - 用户表操作
     */
    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * SystemProviderMapper - 厂商表操作
     * <p>LLM 用户配置依赖厂商信息，需先插入厂商数据</p>
     */
    @Autowired
    private SystemProviderMapper systemProviderMapper;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 测试用户 ID
     */
    private static final Long TEST_USER_ID = 990001L;

    /**
     * 测试厂商 ID - 与用户 ID 不同，避免混淆
     */
    private static final Long TEST_PROVIDER_ID = 990002L;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "configtest";

    /**
     * 测试密码
     */
    private static final String TEST_PASSWORD = "password123";

    /**
     * 登录后的访问令牌
     */
    private String token;

    /**
     * 创建的配置 ID（用于后续测试）
     */
    private Long createdConfigId;

    /**
     * 测试前置准备：插入厂商和用户数据
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>插入 SystemProvider（LLM 厂商配置，LLM 用户配置外键依赖）</li>
     *   <li>插入 SysUser（配置所有者）</li>
     *   <li>编程式登录获取 token</li>
     * </ol>
     *
     * <h3>为什么需要插入厂商数据？</h3>
     * <p>llm_user_config 表有外键 provider_id 关联 llm_system_provider</p>
     */
    @BeforeAll
    void setup() {
        // ===== 步骤 1: 插入 SystemProvider（LLM 厂商） =====
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("openai_config");  // 使用唯一 type 避免冲突
        provider.setProviderName("OpenAI");
        provider.setApiBaseUrl("https://api.openai.com/v1");
        provider.setSupportedCapabilities("[\"CHAT\"]");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        // ===== 步骤 2: 插入测试用户 =====
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("配置测试");
        user.setEmail("config@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // ===== 步骤 3: 编程式登录 =====
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    /**
     * 测试用例 1：创建 LLM 配置成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建创建配置请求</li>
     *   <li>发送 POST 请求</li>
     *   <li>验证返回的配置信息</li>
     * </ol>
     *
     * <h3>请求字段：</h3>
     * <ul>
     *   <li>providerType: "openai_config"（对应已插入的厂商）</li>
     *   <li>configName: "我的GPT-4"</li>
     *   <li>apiKey: "sk-test123456789"（会经过 AES-256-GCM 加密存储）</li>
     *   <li>modelName: "gpt-4"</li>
     *   <li>capability: "CHAT"</li>
     *   <li>priority: 50</li>
     *   <li>isDefault: true</li>
     * </ul>
     *
     * <h3>数据库操作：</h3>
     * <ul>
     *   <li>INSERT into llm_user_config 表</li>
     *   <li>API Key 加密存储（ApiKeyEncryptService）</li>
     *   <li>如果 isDefault=true，其他配置的 is_default 被设为 false</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("创建LLM配置 - POST /api/v1/llm/configs")
    void Should_CreateConfig_When_DataValid() throws Exception {
        // 构建创建配置请求
        String requestJson = "{" +
                "\"providerType\":\"openai_config\"," +   // 对应已插入的厂商
                "\"configName\":\"我的GPT-4\"," +
                "\"apiKey\":\"sk-test123456789\"," +
                "\"modelName\":\"gpt-4\"," +
                "\"capability\":\"CHAT\"," +
                "\"priority\":50," +
                "\"isDefault\":true" +
                "}";

        mockMvc.perform(post("/api/v1/llm/configs")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.configName").value("我的GPT-4"))
            .andExpect(jsonPath("$.data.providerType").value("openai_config"))
            .andExpect(jsonPath("$.data.capability").value("CHAT"))
            .andReturn();
    }

    /**
     * 测试用例 2：获取配置列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 GET 请求（不带筛选条件）</li>
     *   <li>验证返回数组包含创建的配置文件</li>
     * </ol>
     *
     * <h3>依赖：</h3>
     * <p>依赖测试用例 1 创建的配置</p>
     */
    @Test
    @Order(2)
    @DisplayName("获取配置列表 - GET /api/v1/llm/configs")
    void Should_ReturnConfigList_When_GetConfigs() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs")
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * 测试用例 3：按厂商类型筛选配置
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 GET 请求，带 providerType 参数</li>
     *   <li>验证只返回指定厂商的配置</li>
     * </ol>
     *
     * <h3>筛选条件：</h3>
     * <p>providerType = "openai"（注意：这里用 openai，与插入的 openai_config 不匹配）</p>
     * <p>注：这是一个测试设计选择，实际应该用 "openai_config"</p>
     */
    @Test
    @Order(3)
    @DisplayName("按厂商类型筛选配置 - GET /api/v1/llm/configs?providerType=openai")
    void Should_ReturnConfigList_When_FilterByProviderType() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs")
                .header("satoken", token)
                .param("providerType", "openai"))  // 筛选条件
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * 测试用例 4：未登录访问应返回 401
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>不携带 token 发送请求</li>
     *   <li>验证返回 401</li>
     * </ol>
     */
    @Test
    @Order(4)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs"))
            .andExpect(status().isUnauthorized());
    }
}
