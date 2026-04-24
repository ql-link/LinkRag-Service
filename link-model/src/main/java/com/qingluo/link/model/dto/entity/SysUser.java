package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户表
 * 对应表：sys_user
 */
@Data
@TableName("sys_user")
@Schema(description = "系统用户")
public class SysUser {

    @Schema(description = "用户ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "密码哈希")
    private String passwordHash;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "角色", example = "ADMIN")
    private String role;

    @Schema(description = "状态 0-禁用 1-启用", example = "1")
    private Integer status;

    @Schema(description = "最后登录时间")
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}