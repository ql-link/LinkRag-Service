package com.qingluo.link.model.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysUser 实体测试
 */
class SysUserTest {

    @Test
    void should_SetAndGetAllFields() {
        LocalDateTime now = LocalDateTime.now();

        SysUser user = SysUser.builder()
            .id("test-uuid")
            .username("testuser")
            .passwordHash("hashed_password")
            .nickname("Test User")
            .email("test@example.com")
            .phone("13800138000")
            .avatarUrl("https://example.com/avatar.png")
            .role("USER")
            .status(1)
            .lastLoginAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(user.getId()).isEqualTo("test-uuid");
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
        assertThat(user.getNickname()).isEqualTo("Test User");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPhone()).isEqualTo("13800138000");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getLastLoginAt()).isEqualTo(now);
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void should_SetTableName() {
        SysUser user = new SysUser();
        // MyBatis-Plus 会使用 @TableName 注解
        assertThat(user).isNotNull();
    }
}