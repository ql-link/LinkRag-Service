package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult {

    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private Long userId;
}