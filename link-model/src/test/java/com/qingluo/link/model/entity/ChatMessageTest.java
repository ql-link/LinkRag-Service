package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessage 实体类测试
 */
class ChatMessageTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = ChatMessage.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "ChatMessage 应有 @TableName 注解");
        assertEquals("chat_message", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("conversationId");
        assertFieldExists("configId");
        assertFieldExists("modelName");
        assertFieldExists("role");
        assertFieldExists("content");
        assertFieldExists("tokenCount");
        assertFieldExists("createdAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = ChatMessage.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_HaveDefaultTokenCount_When_ObjectCreated() {
        ChatMessage message = new ChatMessage();
        assertEquals(0, message.getTokenCount());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = ChatMessage.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}