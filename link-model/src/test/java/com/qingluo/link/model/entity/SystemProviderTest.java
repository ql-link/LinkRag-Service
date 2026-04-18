package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.SystemProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemProvider 实体类测试
 */
class SystemProviderTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = SystemProvider.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "SystemProvider 应有 @TableName 注解");
        assertEquals("llm_system_provider", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("providerType");
        assertFieldExists("providerName");
        assertFieldExists("apiBaseUrl");
        assertFieldExists("supportedModels");
        assertFieldExists("configSchema");
        assertFieldExists("isActive");
        assertFieldExists("priority");
        assertFieldExists("createdAt");
        assertFieldExists("updatedAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = SystemProvider.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_HaveDefaultIsActive_When_ObjectCreated() {
        SystemProvider provider = new SystemProvider();
        assertTrue(provider.getIsActive());
        assertEquals(50, provider.getPriority());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = SystemProvider.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}