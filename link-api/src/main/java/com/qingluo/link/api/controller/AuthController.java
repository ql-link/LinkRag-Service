package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.model.dto.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @PostMapping("/login")
    public Result<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        // TODO: 调用 AuthService.login()
        return Result.success(new AuthResult());
    }

    @PostMapping("/register")
    public Result<AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        // TODO: 调用 AuthService.register()
        return Result.success(new AuthResult());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // TODO: 调用 AuthService.logout()
        return Result.ok(null);
    }
}