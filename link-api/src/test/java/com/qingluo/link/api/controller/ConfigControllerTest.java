package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
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
 * ConfigController 真实集成测试
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SystemProviderMapper systemProviderMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 990001L;
    private static final Long TEST_PROVIDER_ID = 990002L;
    private static final String TEST_USERNAME = "configtest";
    private static final String TEST_PASSWORD = "password123";

    private String token;
    private Long createdConfigId;

    @BeforeAll
    void setup() {
        // 插入 SystemProvider
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("openai_config");
        provider.setProviderName("OpenAI");
        provider.setApiBaseUrl("https://api.openai.com/v1");
        provider.setSupportedModels("[\"gpt-4\", \"gpt-3.5-turbo\"]");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        // 插入测试用户
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("配置测试");
        user.setEmail("config@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 编程式登录获取 token
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("创建LLM配置 - POST /api/v1/llm/configs")
    void Should_CreateConfig_When_DataValid() throws Exception {
        String requestJson = "{" +
                "\"providerType\":\"openai_config\"," +
                "\"configName\":\"我的GPT-4\"," +
                "\"apiKey\":\"sk-test123456789\"," +
                "\"modelName\":\"gpt-4\"," +
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
            .andReturn();

        // 保存创建的 configId 供后续测试使用
        // 由于创建后返回的 DTO 包含 id，可以在后续测试中重新查询
    }

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

    @Test
    @Order(3)
    @DisplayName("按厂商类型筛选配置 - GET /api/v1/llm/configs?providerType=openai")
    void Should_ReturnConfigList_When_FilterByProviderType() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs")
                .header("satoken", token)
                .param("providerType", "openai"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs"))
            .andExpect(status().isUnauthorized());
    }
}