package com.qingluo.link.model.dto.request;

import lombok.Data;

/**
 * 更新个人资料请求
 */
@Data
public class UpdateProfileRequest {

    private String nickname;

    private String email;

    private String phone;

    private String avatarUrl;
}
