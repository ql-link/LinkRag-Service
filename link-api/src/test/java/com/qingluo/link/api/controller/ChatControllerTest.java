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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 真实集成测试
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
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Long TEST_USER_ID = 99999L;
    private static final String TEST_USERNAME = "chattest";
    private static final String TEST_PASSWORD = "password123";

    private String token;
    private Long createdConversationId;

    @BeforeAll
    void setup() {
        // 直接在数据库插入测试用户，不走 HTTP
        SysUser user = new SysUser();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setNickname("聊天测试");
        user.setEmail("chat@test.com");
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);

        // 编程式登录获取 token
        StpUtil.login(TEST_USER_ID);
        token = StpUtil.getTokenValue();
    }

    @Test
    @Order(1)
    @DisplayName("创建对话 - POST /api/v1/chat/conversations")
    void Should_CreateConversation_When_DataValid() throws Exception {
        String requestJson = "{\"title\":\"测试对话\"}";

        MvcResult result = mockMvc.perform(post("/api/v1/chat/conversations")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.title").value("测试对话"))
            .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        createdConversationId = jsonNode.get("data").get("id").asLong();
        Assertions.assertNotNull(createdConversationId);
    }

    @Test
    @Order(2)
    @DisplayName("获取对话列表 - GET /api/v1/chat/conversations")
    void Should_ReturnConversationList_When_GetConversations() throws Exception {
        mockMvc.perform(get("/api/v1/chat/conversations")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items[0].title").value("测试对话"));
    }

    @Test
    @Order(3)
    @DisplayName("获取对话消息 - GET /api/v1/chat/conversations/{id}/messages")
    void Should_ReturnMessages_When_ConversationExists() throws Exception {
        mockMvc.perform(get("/api/v1/chat/conversations/" + createdConversationId + "/messages")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @Order(4)
    @DisplayName("删除对话 - DELETE /api/v1/chat/conversations/{id}")
    void Should_DeleteConversation_When_ConversationExists() throws Exception {
        mockMvc.perform(delete("/api/v1/chat/conversations/" + createdConversationId)
                .header("satoken", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("未登录访问应返回 401")
    void Should_Return401_When_NotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/chat/conversations"))
            .andExpect(status().isUnauthorized());
    }
}
