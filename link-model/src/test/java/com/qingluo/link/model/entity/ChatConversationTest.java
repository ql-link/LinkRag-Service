package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.ChatConversation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatConversation 实体类测试
 */
class ChatConversationTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = ChatConversation.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "ChatConversation 应有 @TableName 注解");
        assertEquals("chat_conversation", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("userId");
        assertFieldExists("lastConfigId");
        assertFieldExists("lastModelName");
        assertFieldExists("title");
        assertFieldExists("isPinned");
        assertFieldExists("isDeleted");
        assertFieldExists("createdAt");
        assertFieldExists("updatedAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = ChatConversation.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_MarkIsDeletedAsLogicDelete_When_EntityDefined() throws Exception {
        Field deletedField = ChatConversation.class.getDeclaredField("isDeleted");
        TableLogic annotation = deletedField.getAnnotation(TableLogic.class);
        assertNotNull(annotation, "isDeleted 字段应有 @TableLogic 注解");
    }

    @Test
    void Should_HaveDefaultValues_When_ObjectCreated() {
        ChatConversation conversation = new ChatConversation();
        assertFalse(conversation.getIsPinned());
        assertFalse(conversation.getIsDeleted());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = ChatConversation.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}