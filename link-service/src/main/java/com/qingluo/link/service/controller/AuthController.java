package com.qingluo.link.service.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.dto.request.LoginRequest;
import com.qingluo.link.core.dto.request.RegisterRequest;
import com.qingluo.link.core.dto.response.AuthResult;
import com.qingluo.link.core.dto.response.Result;
import com.qingluo.link.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request);
        return Result.success(result);
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.created(null);
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success(null);
    }
}