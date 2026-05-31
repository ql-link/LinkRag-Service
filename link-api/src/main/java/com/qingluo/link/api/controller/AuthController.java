package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器
 * <p>提供用户登录、注册、登出等认证功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证接口", description = "用户登录、注册、登出")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     *
     * @param request 登录请求（account, password）
     * @return 认证结果（accessToken, userId）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名或邮箱加密码登录，返回访问令牌")
    public Result<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 用户注册
     *
     * @param request 注册信息（username, password, email）
     * @return 注册结果（userId）
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册，自动生成默认昵称并分配普通用户角色")
    public Result<AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 退出登录
     *
     * @return 无返回内容
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "当前Token加入黑名单")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok(null);
    }
}
