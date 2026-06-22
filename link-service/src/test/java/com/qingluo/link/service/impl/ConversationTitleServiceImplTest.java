package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.service.config.ConversationTitleProperties;
import com.qingluo.link.service.llm.ChatTitleGenerator;
import java.util.concurrent.Executor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话标题生成单测：模型标题成功回写、失败保留临时标题。
 */
@ExtendWith(MockitoExtension.class)
class ConversationTitleServiceImplTest {

    @Mock
    private ChatConversationMapper conversationMapper;
    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;
    @Mock
    private ChatTitleGenerator chatTitleGenerator;

    private ConversationTitleServiceImpl service;
    private ConversationTitleProperties properties;

    @BeforeAll
    static void initMpTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
            new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ChatConversation.class);
        TableInfoHelper.initTableInfo(assistant, UserLLMConfig.class);
    }

    @BeforeEach
    void setUp() {
        properties = new ConversationTitleProperties();
        properties.setMaxLength(10);
        Executor directExecutor = Runnable::run;
        service = new ConversationTitleServiceImpl(
            conversationMapper, userLLMConfigMapper, chatTitleGenerator, properties, directExecutor);
    }

    @Test
    @DisplayName("首问临时标题会清洗并按最大长度截断")
    void buildFallbackTitle_cleansAndLimitsQuery() {
        assertThat(service.buildFallbackTitle("  “这是一个很长很长的问题标题吗？”  "))
            .isEqualTo("这是一个很长很长的问");
    }

    @Test
    @DisplayName("模型标题生成成功后写回")
    void generateAndUpdate_updatesWhenCurrentTitleIsFallback() {
        UserLLMConfig config = config(7L);
        given(userLLMConfigMapper.selectOne(any())).willReturn(config);
        given(chatTitleGenerator.generate(config, "什么是RAG", "RAG 是检索增强生成")).willReturn("“RAG 入门”");

        service.generateAndUpdate(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");

        verify(conversationMapper).update(any(), any());
    }

    @Test
    @DisplayName("模型标题生成成功后直接覆盖当前标题")
    void generateAndUpdate_overwritesCurrentTitle() {
        UserLLMConfig config = config(7L);
        given(userLLMConfigMapper.selectOne(any())).willReturn(config);
        given(chatTitleGenerator.generate(config, "什么是RAG", "RAG 是检索增强生成")).willReturn("RAG 入门");

        service.generateAndUpdate(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");

        verify(conversationMapper).update(any(), any());
    }

    @Test
    @DisplayName("本轮配置不可用时回退用户默认 Chat 配置")
    void generateAndUpdate_fallsBackToDefaultChatConfig() {
        UserLLMConfig defaultConfig = config(8L);
        given(userLLMConfigMapper.selectOne(any())).willReturn(null, defaultConfig);
        given(chatTitleGenerator.generate(defaultConfig, "什么是RAG", "RAG 是检索增强生成")).willReturn("RAG 入门");

        service.generateAndUpdate(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");

        ArgumentCaptor<UserLLMConfig> captor = ArgumentCaptor.forClass(UserLLMConfig.class);
        verify(chatTitleGenerator).generate(captor.capture(), any(), any());
        assertThat(captor.getValue().getId()).isEqualTo(8L);
        verify(conversationMapper).update(any(), any());
    }

    @Test
    @DisplayName("模型返回空标题时保留临时标题")
    void generateAndUpdate_keepsFallbackWhenGeneratedBlank() {
        UserLLMConfig config = config(7L);
        given(userLLMConfigMapper.selectOne(any())).willReturn(config);
        given(chatTitleGenerator.generate(config, "什么是RAG", "RAG 是检索增强生成")).willReturn("   ");

        service.generateAndUpdate(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");

        verify(conversationMapper, never()).update(any(), any());
    }

    private UserLLMConfig config(Long id) {
        UserLLMConfig config = new UserLLMConfig();
        config.setId(id);
        config.setUserId(42L);
        config.setCapability("CHAT");
        config.setIsActive(true);
        config.setIsDefault(true);
        config.setProtocol("openai");
        config.setModelName("gpt-4o-mini");
        config.setApiBaseUrl("https://api.example.com/v1");
        config.setApiKey("ENC");
        return config;
    }

}
