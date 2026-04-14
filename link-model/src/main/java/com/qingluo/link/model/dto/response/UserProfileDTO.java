package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息")
public class UserProfileDTO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

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
}