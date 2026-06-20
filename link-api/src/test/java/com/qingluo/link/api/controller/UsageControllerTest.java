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
 * UsageController（LLM 使用量查询）真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>获取使用量汇总 {@code GET /api/v1/llm/usage/summary}</li>
 *   <li>获取日报列表 {@code GET /api/v1/llm/usage/daily}</li>
 *   <li>获取使用日志 {@code GET /api/v1/llm/usage/logs}</li>
 *   <li>未登录访问校验</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → UsageQueryService → UsageLogMapper → H2 Database</p>
 *
 * <h2>前置数据</h2>
 * <ul>
 *   <li>SystemProvider: ID=99993L, type="openai"</li>
 *   <li>SysUser: ID=99993L（用量记录所有者）</li>
 *   <li>UsageLog: 2条记录（gpt-4 和 gpt-3.5-turbo 各一条）</li>
 * </ul>
 *
 * <h2>测试数据说明</h2>
 * <p>插入 2 条 UsageLog 用于验证聚合查询：</p>
 * <ul>
 *   <li>log1: gpt-4, tokens=150, latency=1000ms</li>
 *   <li>log2: gpt-3.5-turbo, tokens=300, latency=800ms</li>
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
class UsageControllerTest {

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
     * SystemProviderMapper - 厂商表操作（提供外键约束）
     */
    @Autowired
    private SystemProviderMapper systemProviderMapper;

    /**
     * UsageLogMapper - 用量日志表操作
     * <p>用于插入测试用的用量记录</p>
     */
    @Autowired
    private UsageLogMapper usageLogMapper;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 测试用户 ID
     */
    private static final Long TEST_USER_ID = 99993L;

    /**
     * 测试厂商 ID
     */
    private static final Long TEST_PROVIDER_ID = 99993L;

    /**
     * 测试配置 ID
     */
    private static final Long TEST_CONFIG_ID = 99993L;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "usagetest";

    /**
     * 测试密码
     */
    private static final String TEST_PASSWORD = "password123";

    /**
     * 登录后的访问令牌
     */
    private String token;

    /**
     * 测试前置准备：插入厂商、用户和用量记录
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>插入 SystemProvider（外键依赖）</li>
     *   <li>插入 SysUser</li>
     *   <li>插入 2 条 UsageLog（用于聚合查询测试）</li>
     *   <li>编程式登录</li>
     * </ol>
     */
    @BeforeAll
    void setup() {
        // ===== 步骤 1: 插入 SystemProvider（外键依赖） =====
        SystemProvider provider = new SystemProvider();
        provider.setId(TEST_PROVIDER_ID);
        provider.setProviderType("openai");
        provider.setProviderName("OpenAI");
        provider.setApiBaseUrl("https://api.openai.com/v1");
        provider.setIsActive(true);
        provider.setPriority(50);
        systemProviderMapper.insert(provider);

        // ===== 步骤 2: 插入测试用户 =====
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("用量测试");
        user.setEmail("usage@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // ===== 步骤 3: 插入 UsageLog 数据（用于聚合查询） =====

        // 用量记录 1: GPT-4 调用
        UsageLog log1 = new UsageLog();
        log1.setUserId(TEST_USER_ID);
        log1.setConfigId(TEST_CONFIG_ID);
        log1.setProviderType("openai");
        log1.setModelName("gpt-4");
        log1.setStage("chat");          // 调用阶段
        log1.setOperation("generate");  // 调用操作
        log1.setPromptTokens(100);      // 提示词 token 数
        log1.setCompletionTokens(50);   // 补全 token 数
        log1.setTotalTokens(150);       // 总 token 数
        log1.setLatencyMs(1000);        // 延迟（毫秒）
        log1.setStatus("success");
        log1.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log1);

        // 用量记录 2: GPT-3.5-Turbo 调用
        UsageLog log2 = new UsageLog();
        log2.setUserId(TEST_USER_ID);
        log2.setConfigId(TEST_CONFIG_ID);
        log2.setProviderType("openai");
        log2.setModelName("gpt-3.5-turbo");
        log2.setStage("chat");
        log2.setOperation("generate");
        log2.setPromptTokens(200);
        log2.setCompletionTokens(100);
        log2.setTotalTokens(300);
        log2.setLatencyMs(800);
        log2.setStatus("success");
        log2.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log2);

        // 用量记录 3: 全链路 usage_report 通道写入的解析侧 embed 行（非对话）
        // 用于验证读侧按 stage 过滤：缺省仅统计 chat，应排除本行；stage=all 才纳入。
        UsageLog log3 = new UsageLog();
        log3.setUserId(TEST_USER_ID);
        log3.setConfigId(null);          // 系统配置调用 → config_id 为 NULL
        log3.setProviderType("openai");
        log3.setModelName("text-embedding-3-large");
        log3.setStage("parse");
        log3.setOperation("embed");
        log3.setPromptTokens(12840);
        log3.setCompletionTokens(0);     // 向量类恒 0
        log3.setTotalTokens(12840);
        log3.setStatus("success");
        log3.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log3);

        // ===== 步骤 4: 编程式登录 =====
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    /**
     * 测试用例 1：获取使用量汇总
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建日期范围参数（今天 - 7天）</li>
     *   <li>发送 GET 请求</li>
     *   <li>验证返回 200</li>
     * </ol>
     *
     * <h3>汇总数据来源：</h3>
     * <ul>
     *   <li>总调用次数: 2</li>
     *   <li>总 token 数: 150 + 300 = 450</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("获取使用量汇总 - GET /api/v1/llm/usage/summary")
    void Should_ReturnUsageSummary_When_GetSummary() throws Exception {
        // 计算日期范围：今天 - 7天
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        // 缺省 stage：仅统计对话(chat) 两行，排除 parse·embed 行（口径与 LINK-184 前一致）。
        mockMvc.perform(get("/api/v1/llm/usage/summary")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.totalCalls").value(2))
            .andExpect(jsonPath("$.data.totalTokens").value(450));
    }

    /**
     * 测试用例 1b：stage=all 统计全链路（含 parse·embed 行）。
     */
    @Test
    @Order(5)
    @DisplayName("全链路用量汇总 - GET /api/v1/llm/usage/summary?stage=all")
    void Should_IncludeAllStages_When_StageAll() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/summary")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today)
                .param("stage", "all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCalls").value(3))
            .andExpect(jsonPath("$.data.totalTokens").value(13290));
    }

    /**
     * 测试用例 1c：明细行暴露 stage/operation，且缺省仅返回 chat 行。
     */
    @Test
    @Order(6)
    @DisplayName("明细暴露 stage/operation 且缺省仅 chat - GET /api/v1/llm/usage/logs")
    void Should_ExposeStageAndScopeToChat_When_GetUsageLogsDefault() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/logs")
                .header("satoken", token)
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].stage").value("chat"))
            .andExpect(jsonPath("$.data.items[0].operation").value("generate"));
    }

    /**
     * 测试用例 2：获取日报列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 GET 请求（带日期范围）</li>
     *   <li>验证返回数组</li>
     * </ol>
     *
     * <h3>返回数据格式：</h3>
     * <p>按日期聚合的用量数据，包含每日调用次数和 token 消耗</p>
     */
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

    /**
     * 测试用例 3：获取使用日志
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>发送 GET 请求（带分页和日期范围）</li>
     *   <li>验证返回 200</li>
     * </ol>
     *
     * <h3>返回数据：</h3>
     * <p>分页的调用日志列表，包含每条记录的模型名、token 数、延迟等</p>
     */
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
        String today = java.time.LocalDate.now().toString();
        String startDate = java.time.LocalDate.now().minusDays(7).toString();

        mockMvc.perform(get("/api/v1/llm/usage/summary")
                .param("startDate", startDate)
                .param("endDate", today))
            .andExpect(status().isUnauthorized());
    }
}