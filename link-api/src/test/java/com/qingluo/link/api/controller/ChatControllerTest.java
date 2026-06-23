package com.qingluo.link.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 真实集成测试
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li>创建对话 {@code POST /api/v1/chat/conversations}</li>
 *   <li>获取对话列表 {@code GET /api/v1/chat/conversations}</li>
 *   <li>获取对话消息 {@code GET /api/v1/chat/conversations/{id}/messages}</li>
 *   <li>删除对话 {@code DELETE /api/v1/chat/conversations/{id}}</li>
 *   <li>未登录访问校验</li>
 * </ul>
 *
 * <h2>测试链路</h2>
 * <p>MockMvc → Controller → ChatService → ChatConversationMapper/ChatMessageMapper → H2 Database</p>
 *
 * <h2>测试数据</h2>
 * <ul>
 *   <li>测试用户 ID: {@code 99991L} - 直接插入 sys_user 表</li>
 *   <li>测试对话 ID: 从创建响应中提取，用于后续查询/删除操作</li>
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
class ChatControllerTest {

    /**
     * MockMvc - 模拟 HTTP 请求，无需启动真实服务器
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * ObjectMapper - JSON 序列化/反序列化
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * SysUserMapper - 直接操作数据库插入测试用户
     */
    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * PasswordEncoder - BCrypt 密码加密
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试用户 ID - 手动指定避免与其他测试冲突
     */
    private static final Long TEST_USER_ID = 99991L;

    /**
     * 测试用户名
     */
    private static final String TEST_USERNAME = "chattest";

    /**
     * 测试密码
     */
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_DATASET_NAME = "默认数据集";

    /**
     * 登录后的访问令牌 (sa-token)
     */
    private String token;
    private Long datasetId;

    /**
     * 创建的对话 ID - 用于后续测试（查询消息、删除）
     * Instance 级别变量，因为需要在多个测试方法间共享
     */
    private Long createdConversationId;

    /**
     * 测试前置准备：插入测试用户并登录
     *
     * <h3>初始化步骤：</h3>
     * <ol>
     *   <li>创建测试用户实体（SysUser）</li>
     *   <li>使用 BCrypt 加密密码</li>
     *   <li>通过 SysUserMapper 直接插入 H2 数据库</li>
     *   <li>使用 StpUtil 编程式登录获取 token</li>
     * </ol>
     *
     * <h3>为什么不用 HTTP 接口创建用户？</h3>
     * <p>避免依赖 AuthController 的实现，让 ChatController 测试独立</p>
     */
    @BeforeAll
    void setup() {
        jdbcTemplate.update("DELETE FROM chat_message");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        // ===== 步骤 1: 创建测试用户 =====
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        // 使用 BCrypt 加密密码（生产环境存储的是加密后的 hash）
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("聊天测试");
        user.setEmail("chat@test.com");
        user.setRole("USER");
        user.setStatus(1);

        // 插入数据库（绕过 HTTP，直接操作 Mapper）
        sysUserMapper.insert(user);

        // ===== 步骤 2: 编程式登录获取 token =====
        // 使用 sa-token 的编程式 API，模拟已登录状态
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();

        datasetId = ensureDatasetExists();
    }

    /**
     * 测试用例 1：创建对话成功
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>构建创建对话请求（title: "测试对话"）</li>
     *   <li>携带 sa-token header 发送 POST 请求</li>
     *   <li>验证响应状态码、code、返回的对话标题</li>
     *   <li>从响应中提取 createdConversationId 供后续测试使用</li>
     * </ol>
     *
     * <h3>数据库操作：</h3>
     * <ul>
     *   <li>INSERT into chat_conversation 表</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("创建对话 - POST /api/v1/chat/conversations")
    void Should_CreateConversation_When_DataValid() throws Exception {
        // 构建请求 JSON
        String requestJson = "{\"title\":\"测试对话\",\"datasetId\":" + datasetId + "}";

        // 发送创建对话请求
        MvcResult result = mockMvc.perform(post("/api/v1/chat/conversations")
                .header("satoken", token)                      // 携带认证 token
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            // 验证 HTTP 200
            .andExpect(status().isOk())
            // 验证业务 code
            .andExpect(jsonPath("$.code").value(200))
            // 验证返回的对话标题
            .andExpect(jsonPath("$.data.title").value("测试对话"))
            .andExpect(jsonPath("$.data.datasetId").value(datasetId))
            .andReturn();

        // ===== 提取 createdConversationId 供后续测试使用 =====
        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        createdConversationId = jsonNode.get("data").get("id").asLong();

        // 断言：确保 conversationId 不为空
        Assertions.assertNotNull(createdConversationId,
            "创建的对话 ID 不应该为空，用于后续测试");
    }

    /**
     * 测试用例 2：获取对话列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>携带 sa-token 发送 GET 请求（分页参数）</li>
     *   <li>验证响应包含分页数据</li>
     *   <li>验证列表第一项是刚才创建的对话</li>
     * </ol>
     *
     * <h3>依赖：</h3>
     * <p>依赖测试用例 1（创建对话）已执行，数据已插入数据库</p>
     */
    @Test
    @Order(2)
    @DisplayName("获取对话列表 - GET /api/v1/chat/conversations")
    void Should_ReturnConversationList_When_GetConversations() throws Exception {
        Assertions.assertNotNull(datasetId);
        mockMvc.perform(get("/api/v1/chat/conversations")
                .header("satoken", token)
                .param("page", "1")     // 分页页码
                .param("pageSize", "20")) // 每页数量
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            // 验证 data.items 是数组
            .andExpect(jsonPath("$.data.items").isArray())
            // 验证列表第一项的标题（依赖 Order(1) 创建的数据）
            .andExpect(jsonPath("$.data.items[0].title").value("测试对话"))
            .andExpect(jsonPath("$.data.items[0].datasetId").value(datasetId));
    }

    /**
     * 测试用例 3：获取对话消息列表
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>使用 createdConversationId 发送 GET 请求</li>
     *   <li>验证响应包含消息列表（空数组，因为刚创建暂无消息）</li>
     * </ol>
     *
     * <h3>依赖：</h3>
     * <p>依赖测试用例 1 创建的 conversationId</p>
     */
    @Test
    @Order(3)
    @DisplayName("获取对话消息 - GET /api/v1/chat/conversations/{id}/messages")
    void Should_ReturnMessages_When_ConversationExists() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO chat_message (
                    conversation_id, config_id, model_name, `query`, answer, `references`, request_id, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, DATEADD('SECOND', -2, CURRENT_TIMESTAMP))
                """,
            createdConversationId,
            7L,
            "gpt-4",
            "什么是RAG？",
            "RAG 是检索增强生成。",
            "[\"chunk-1\",\"chunk-2\"]",
            "req-chat-list-1",
            "success");
        jdbcTemplate.update("""
                INSERT INTO chat_message (
                    conversation_id, config_id, model_name, `query`, answer, `references`, request_id, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, DATEADD('SECOND', -1, CURRENT_TIMESTAMP))
                """,
            createdConversationId,
            8L,
            "gpt-4o-mini",
            "失败时会返回什么？",
            null,
            "[]",
            "req-chat-list-2",
            "failed");

        mockMvc.perform(get("/api/v1/chat/conversations/" + createdConversationId + "/messages")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].conversationId").value(createdConversationId))
            .andExpect(jsonPath("$.data.items[0].query").value("什么是RAG？"))
            .andExpect(jsonPath("$.data.items[0].answer").value("RAG 是检索增强生成。"))
            .andExpect(jsonPath("$.data.items[0].configId").value(7))
            .andExpect(jsonPath("$.data.items[0].modelName").value("gpt-4"))
            .andExpect(jsonPath("$.data.items[0].references[0]").value("chunk-1"))
            .andExpect(jsonPath("$.data.items[0].references[1]").value("chunk-2"))
            .andExpect(jsonPath("$.data.items[0].requestId").value("req-chat-list-1"))
            .andExpect(jsonPath("$.data.items[0].status").value("success"))
            .andExpect(jsonPath("$.data.items[1].query").value("失败时会返回什么？"))
            .andExpect(jsonPath("$.data.items[1].answer").doesNotExist())
            .andExpect(jsonPath("$.data.items[1].requestId").value("req-chat-list-2"))
            .andExpect(jsonPath("$.data.items[1].status").value("failed"));
    }

    // 发送消息接口已下线：对话轮次改由 Python 通过 tolink.rag.chat_turn 上报、Java 落库（LINK-180）。

    /**
     * 测试用例 5：更新对话（置顶）
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>更新对话置顶状态为 true</li>
     *   <li>验证响应中的 isPinned 字段</li>
     * </ol>
     */
    @Test
    @Order(5)
    @DisplayName("更新对话 - PATCH /api/v1/chat/conversations/{id}")
    void Should_UpdateConversation_When_DataValid() throws Exception {
        String requestJson = "{\"isPinned\":true}";

        mockMvc.perform(patch("/api/v1/chat/conversations/" + createdConversationId)
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(createdConversationId))
            .andExpect(jsonPath("$.data.isPinned").value(true));
    }

    /**
     * 测试用例 4：删除对话
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>使用 createdConversationId 发送 DELETE 请求</li>
     *   <li>验证删除成功（200）</li>
     * </ol>
     *
     * <h3>数据库操作：</h3>
     * <ul>
     *   <li>DELETE FROM chat_conversation（物理删除）</li>
     * </ul>
     *
     * <h3>依赖：</h3>
     * <p>依赖测试用例 1 创建的 conversationId</p>
     */
    @Test
    @Order(6)
    @DisplayName("删除对话 - DELETE /api/v1/chat/conversations/{id}")
    void Should_DeleteConversation_When_ConversationExists() throws Exception {
        mockMvc.perform(delete("/api/v1/chat/conversations/" + createdConversationId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试用例 5：未登录访问应返回 401
     *
     * <h3>测试步骤：</h3>
     * <ol>
     *   <li>不携带任何认证信息发送 GET 请求</li>
     *   <li>验证返回 401 Unauthorized</li>
     * </ol>
     *
     * <h3>验证点：</h3>
     * <p>验证 sa-token 认证拦截器正常工作</p>
     */
    @Test
    @Order(7)
    @DisplayName("允许同一数据集下创建重名对话")
    void Should_AllowDuplicateConversationTitle_When_SameDataset() throws Exception {
        String requestJson = "{\"title\":\"重复标题\",\"datasetId\":" + datasetId + "}";

        MvcResult first = mockMvc.perform(post("/api/v1/chat/conversations")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("重复标题"))
            .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/chat/conversations")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("重复标题"))
            .andReturn();

        deleteConversationFromResponse(first);
        deleteConversationFromResponse(second);
    }

    @Test
    @Order(8)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        // 不携带 satoken header
        mockMvc.perform(get("/api/v1/chat/conversations"))
            .andExpect(status().isUnauthorized());
    }

    private Long ensureDatasetExists() {
        String datasetName = TEST_DATASET_NAME + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO dataset (user_id, name, description, status)
                VALUES (?, ?, '测试数据集', 'ACTIVE')
                """, TEST_USER_ID, datasetName);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM dataset WHERE user_id = ? AND name = ?",
            Long.class,
            TEST_USER_ID,
            datasetName
        );
    }

    private void deleteConversationFromResponse(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        Long conversationId = objectMapper.readTree(response).get("data").get("id").asLong();
        mockMvc.perform(delete("/api/v1/chat/conversations/" + conversationId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
