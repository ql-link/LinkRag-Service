package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.response.AuthResult;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    AuthResult login(LoginRequest request);

    /**
     * 用户注册
     */
    AuthResult register(RegisterRequest request);

    /**
     * 用户登出
     */
    void logout();
}