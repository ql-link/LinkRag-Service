package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AuthService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
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
}
