package com.qingluo.link.service;

import com.qingluo.link.core.dto.request.LoginRequest;
import com.qingluo.link.core.dto.request.RegisterRequest;
import com.qingluo.link.core.dto.response.AuthResult;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 登录并返回 Token 信息
     * @return 包含 accessToken、tokenType、expiresIn 的认证结果
     */
    AuthResult login(LoginRequest request);

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 退出登录
     */
    void logout();
}