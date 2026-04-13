package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.qingluo.link.model.dto.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysUser 实体类测试
 *
 * 测试场景：
 * - Should_HaveCorrectTableName_When_EntityDefined
 * - Should_HaveCorrectFields_When_EntityDefined
 * - Should_MarkIdAsAutoIncrement_When_EntityDefined
 */
class SysUserTest {

    @Test
    void Should_HaveCorrectTableName_When_EntityDefined() {
        TableName annotation = SysUser.class.getAnnotation(TableName.class);
        assertNotNull(annotation, "SysUser 应有 @TableName 注解");
        assertEquals("sys_user", annotation.value());
    }

    @Test
    void Should_HaveCorrectFields_When_EntityDefined() throws Exception {
        // 验证必要字段存在
        assertFieldExists("id");
        assertFieldExists("username");
        assertFieldExists("passwordHash");
        assertFieldExists("nickname");
        assertFieldExists("email");
        assertFieldExists("phone");
        assertFieldExists("avatarUrl");
        assertFieldExists("role");
        assertFieldExists("status");
        assertFieldExists("lastLoginAt");
        assertFieldExists("createdAt");
        assertFieldExists("updatedAt");
    }

    @Test
    void Should_MarkIdAsAutoIncrement_When_EntityDefined() throws Exception {
        Field idField = SysUser.class.getDeclaredField("id");
        TableId annotation = idField.getAnnotation(TableId.class);
        assertNotNull(annotation, "id 字段应有 @TableId 注解");
        assertEquals(IdType.AUTO, annotation.type());
    }

    @Test
    void Should_GenerateGettersAndSetters_When_LombokWorks() throws Exception {
        SysUser user = new SysUser();

        // 测试 setter
        user.setId(10000L);
        user.setUsername("testuser");
        user.setPasswordHash("hashedPassword");
        user.setNickname("Test User");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setStatus(1);

        // 测试 getter
        assertEquals(10000L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("hashedPassword", user.getPasswordHash());
        assertEquals("Test User", user.getNickname());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("USER", user.getRole());
        assertEquals(1, user.getStatus());
    }

    private void assertFieldExists(String fieldName) throws Exception {
        Field field = SysUser.class.getDeclaredField(fieldName);
        assertNotNull(field, "字段 " + fieldName + " 应该存在");
    }
}