package com.qingluo.link.service.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.dto.response.Result;
import com.qingluo.link.core.dto.response.UserProfileDTO;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.entity.SysUser;
import com.qingluo.link.service.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper sysUserMapper;

    @SaCheckLogin
    @GetMapping("/profile")
    public Result<UserProfileDTO> getProfile() {
        String userId = AuthContext.getCurrentUserId();
        SysUser user = sysUserMapper.selectById(userId);

        UserProfileDTO profile = UserProfileDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .role(user.getRole())
            .build();

        return Result.success(profile);
    }
}