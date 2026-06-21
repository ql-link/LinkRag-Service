package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.UpdateProfileRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AuthService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器
 * <p>提供用户个人信息的查看与修改功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "用户接口", description = "获取和修改当前用户个人信息")
public class UserController {

    private final AuthService authService;

    /**
     * 获取当前用户信息
     *
     * @return 用户详细信息（id, username, nickname, email, role, status）
     */
    @GetMapping("/profile")
    @SaCheckLogin
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    public Result<UserProfileDTO> getProfile() {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(authService.getProfile(userId));
    }

    /**
     * 修改个人资料
     *
     * @param request 修改内容（nickname, email, phone, avatarUrl）
     * @return 无返回内容
     */
    @PatchMapping("/profile")
    @SaCheckLogin
    @Operation(summary = "修改个人资料", description = "修改当前用户的个人资料，username和role不可修改")
    public Result<Void> updateProfile(@RequestBody @Validated UpdateProfileRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        authService.updateProfile(userId, request);
        return Result.success(null);
    }

    /**
     * 上传并修改当前用户头像。
     *
     * @param file 头像图片文件
     * @return 更新后的用户资料
     */
    @PostMapping("/avatar")
    @SaCheckLogin
    @Operation(summary = "上传用户头像", description = "上传头像图片到公开 OSS，并更新当前用户头像地址")
    public Result<UserProfileDTO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(authService.uploadAvatar(userId, file));
    }
}
