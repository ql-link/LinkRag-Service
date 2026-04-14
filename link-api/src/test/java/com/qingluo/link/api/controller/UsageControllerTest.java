package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UsageLog;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.mapper.UsageLogMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UsageController 真实集成测试
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
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SystemProviderMapper systemProviderMapper;

    @Autowired
    private UsageLogMapper usageLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 99993L;
    private static final Long TEST_PROVIDER_ID = 99993L;
    private static final Long TEST_CONFIG_ID = 99993L;
    private static final String TEST_USERNAME = "usagetest";
    private static final String TEST_PASSWORD = "password123";

    private String token;

    @BeforeAll
    void setup() {
        // 插入 SystemProvider
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("openai");
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
        user.setNickname("用量测试");
        user.setEmail("usage@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 插入 UsageLog 数据
        UsageLog log1 = new UsageLog();
        log1.setUserId(TEST_USER_ID);
        log1.setConfigId(TEST_CONFIG_ID);
        log1.setProviderType("openai");
        log1.setModelName("gpt-4");
        log1.setPromptTokens(100);
        log1.setCompletionTokens(50);
        log1.setTotalTokens(150);
        log1.setLatencyMs(1000);
        log1.setStatus("success");
        log1.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log1);

        UsageLog log2 = new UsageLog();
        log2.setUserId(TEST_USER_ID);
        log2.setConfigId(TEST_CONFIG_ID);
        log2.setProviderType("openai");
        log2.setModelName("gpt-3.5-turbo");
        log2.setPromptTokens(200);
        log2.setCompletionTokens(100);
        log2.setTotalTokens(300);
        log2.setLatencyMs(800);
        log2.setStatus("success");
        log2.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log2);

        // 编程式登录获取 token
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("获取使用量汇总 - GET /api/v1/llm/usage/summary")
    void Should_ReturnUsageSummary_When_GetSummary() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/summary")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("获取日报列表 - GET /api/v1/llm/usage/daily")
    void Should_ReturnDailyUsageList_When_GetDailyUsage() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/daily")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(3)
    @DisplayName("获取使用日志 - GET /api/v1/llm/usage/logs")
    void Should_ReturnUsageLogs_When_GetUsageLogs() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/logs")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today)
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/summary")
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isUnauthorized());
    }
}