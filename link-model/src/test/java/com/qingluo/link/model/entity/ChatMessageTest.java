package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qingluo.link.model.dto.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessage 实体类测试（一行一轮：query + answer 同行）。
 */
class ChatMessageTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = ChatMessage.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "ChatMessage 应有 @TableName 注解");
        assertEquals("chat_message", annotation.value());
        assertTrue(annotation.autoResultMap(), "references JSON 列查询回填需开启 autoResultMap");
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("conversationId");
        assertFieldExists("configId");
        assertFieldExists("modelName");
        assertFieldExists("query");
        assertFieldExists("answer");
        assertFieldExists("references");
        assertFieldExists("requestId");
        assertFieldExists("status");
        assertFieldExists("createdAt");
    }

    @Test
    void Should_NotHaveLegacyFields_When_EntityDefined() {
        assertThrows(NoSuchFieldException.class, () -> ChatMessage.class.getDeclaredField("role"));
        assertThrows(NoSuchFieldException.class, () -> ChatMessage.class.getDeclaredField("content"));
        assertThrows(NoSuchFieldException.class, () -> ChatMessage.class.getDeclaredField("tokenCount"));
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = ChatMessage.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_MapReferencesWithJsonTypeHandler_When_EntityDefined() throws Exception {
        Field references = ChatMessage.class.getDeclaredField("references");
        TableField annotation = references.getAnnotation(TableField.class);
        assertNotNull(annotation, "references 字段应有 @TableField 注解");
        assertEquals("`references`", annotation.value(), "references 为 MySQL 保留字，列名需反引号包裹");
        assertEquals(JacksonTypeHandler.class, annotation.typeHandler());
        assertEquals(List.class, references.getType());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = ChatMessage.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}
