package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.mapper.ProviderModelMapper;
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
 * ConfigController（LLM 两步配置）真实集成测试。
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → UserLLMConfigService → Mapper → H2；API Key 经真实 AES-256-GCM 加密。</p>
 *
 * <h2>覆盖</h2>
 * <ul>
 *   <li>配置厂商展开整厂商模型 {@code POST /api/v1/llm/configs/setup-provider}</li>
 *   <li>按能力选生效 + 取回 {@code PUT /effective}、{@code GET /default}</li>
 *   <li>模型启停 {@code PATCH /toggle-model}</li>
 *   <li>未登录访问校验</li>
 * </ul>
 *
 * <h2>前置数据</h2>
 * <ul>
 *   <li>SystemProvider: ID=990002L, type="openai_config"</li>
 *   <li>ProviderModel: gpt-4(CHAT,VISION)、gpt-35-turbo(CHAT)</li>
 *   <li>SysUser: ID=990001L（配置所有者）</li>
 * </ul>
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
    private ProviderModelMapper providerModelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 990001L;
    private static final Long TEST_PROVIDER_ID = 990002L;
    private static final String TEST_USERNAME = "configtest";
    private static final String TEST_PASSWORD = "password123";

    private String token;

    /**
     * 前置：插入厂商 + 模型能力目录 + 用户，并编程式登录。
     * 模型能力目录是配置厂商展开的数据源，必须先于 setup-provider 写入。
     */
    @BeforeAll
    void setup() {
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("openai_config");
        provider.setProviderName("OpenAI");
        provider.setApiBaseUrl("https://api.openai.com/v1");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        insertModel("gpt-4", "CHAT");
        insertModel("gpt-4", "VISION");
        insertModel("gpt-35-turbo", "CHAT");

        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("配置测试");
        user.setEmail("config@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    private void insertModel(String modelName, String capability) {
        ProviderModel model = new ProviderModel();
        model.setProviderId(TEST_PROVIDER_ID);
        model.setModelName(modelName);
        model.setCapability(capability);
        // 协议与入口是事实来源，setup-provider 展开时复制为用户配置快照，必须非空
        model.setProtocol("openai");
        model.setApiBaseUrl("https://api.openai.com/v1");
        model.setIsActive(true);
        providerModelMapper.insert(model);
    }

    @Test
    @Order(1)
    @DisplayName("配置厂商自动展开整厂商模型 - POST /setup-provider")
    void Should_ExpandModels_When_SetupProvider() throws Exception {
        String requestJson = "{\"providerType\":\"openai_config\",\"apiKey\":\"sk-test123456789\"}";

        mockMvc.perform(post("/api/v1/llm/configs/setup-provider")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].isSystemPreset").value(false))
            // 用户配置快照携带从模型能力层复制的协议
            .andExpect(jsonPath("$.data[0].protocol").value("openai"));
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
    @DisplayName("按能力选生效并取回生效配置 - PUT /effective + GET /default")
    void Should_SelectEffective_When_PutEffective() throws Exception {
        String requestJson = "{\"capability\":\"CHAT\",\"providerType\":\"openai_config\",\"modelName\":\"gpt-4\"}";

        mockMvc.perform(put("/api/v1/llm/configs/effective")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/llm/configs/default")
                .header("satoken", token)
                .param("capability", "CHAT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.source").value("USER"))
            .andExpect(jsonPath("$.data.configId").isNumber())
            .andExpect(jsonPath("$.data.modelName").value("gpt-4"))
            .andExpect(jsonPath("$.data.providerType").value("openai_config"));
    }

    @Test
    @Order(4)
    @DisplayName("模型启停 - PATCH /toggle-model")
    void Should_ToggleModel_When_PatchToggle() throws Exception {
        String requestJson = "{\"providerType\":\"openai_config\",\"modelName\":\"gpt-35-turbo\",\"enabled\":false}";

        mockMvc.perform(patch("/api/v1/llm/configs/toggle-model")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 关停后按 isActive=false 过滤应能查到被关停的模型行
        mockMvc.perform(get("/api/v1/llm/configs")
                .header("satoken", token)
                .param("isActive", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(5)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/llm/configs"))
            .andExpect(status().isUnauthorized());
    }
}
