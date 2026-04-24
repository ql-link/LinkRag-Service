package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认证结果")
public class AuthResult {

    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "过期时间(秒)", example = "604800")
    private long expiresIn;

    @Schema(description = "用户ID", example = "1")
    private Long userId;
}