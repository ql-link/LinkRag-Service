package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.UsageLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UsageLog 实体类测试
 */
class UsageLogTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = UsageLog.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "UsageLog 应有 @TableName 注解");
        assertEquals("llm_usage_log", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("userId");
        assertFieldExists("configId");
        assertFieldExists("providerType");
        assertFieldExists("modelName");
        assertFieldExists("promptTokens");
        assertFieldExists("completionTokens");
        assertFieldExists("totalTokens");
        assertFieldExists("latencyMs");
        assertFieldExists("status");
        assertFieldExists("errorMessage");
        assertFieldExists("fallbackConfigId");
        assertFieldExists("conversationId");
        assertFieldExists("createdAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = UsageLog.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = UsageLog.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}