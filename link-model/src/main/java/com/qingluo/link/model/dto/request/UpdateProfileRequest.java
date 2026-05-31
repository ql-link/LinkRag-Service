package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新个人资料请求
 */
@Data
@Schema(description = "更新个人资料请求")
public class UpdateProfileRequest {

    @Schema(description = "昵称", example = "新昵称")
    private String nickname;

    @Schema(description = "邮箱", example = "new@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "头像URL")
    private String avatarUrl;
}
