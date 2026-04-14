package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.UpdateProfileRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AuthService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/profile")
    @SaCheckLogin
    public Result<UserProfileDTO> getProfile() {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(authService.getProfile(userId));
    }

    @PatchMapping("/profile")
    @SaCheckLogin
    public Result<Void> updateProfile(@RequestBody @Validated UpdateProfileRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        authService.updateProfile(userId, request);
        return Result.success(null);
    }
}
