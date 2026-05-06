package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserLLMConfig 实体类测试
 */
class UserLLMConfigTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = UserLLMConfig.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "UserLLMConfig 应有 @TableName 注解");
        assertEquals("llm_user_config", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        assertFieldExists("id");
        assertFieldExists("userId");
        assertFieldExists("providerId");
        assertFieldExists("providerType");
        assertFieldExists("providerName");
        assertFieldExists("configName");
        assertFieldExists("apiKey");
        assertFieldExists("customApiBaseUrl");
        assertFieldExists("modelName");
        assertFieldExists("priority");
        assertFieldExists("isActive");
        assertFieldExists("isDefault");
        assertFieldExists("timeoutMs");
        assertFieldExists("maxRetries");
        assertFieldExists("streamEnabled");
        assertFieldExists("capability");
        assertFieldExists("extraConfig");
        assertFieldExists("createdAt");
        assertFieldExists("updatedAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = UserLLMConfig.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_HaveDefaultValues_When_ObjectCreated() {
        UserLLMConfig config = new UserLLMConfig();
        assertTrue(config.getIsActive());
        assertFalse(config.getIsDefault());
        assertEquals(60000, config.getTimeoutMs());
        assertEquals(3, config.getMaxRetries());
        assertTrue(config.getStreamEnabled());
        assertEquals(50, config.getPriority());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = UserLLMConfig.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}
