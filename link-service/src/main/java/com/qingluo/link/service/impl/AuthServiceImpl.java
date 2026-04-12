package com.qingluo.link.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.core.dto.request.LoginRequest;
import com.qingluo.link.core.dto.request.RegisterRequest;
import com.qingluo.link.core.dto.response.AuthResult;
import com.qingluo.link.core.enums.ErrorCode;
import com.qingluo.link.core.exception.AuthException;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.entity.SysUser;
import com.qingluo.link.service.AuthService;
import com.qingluo.link.service.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResult login(LoginRequest request) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new AuthException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(ErrorCode.INVALID_PASSWORD, "密码错误");
        }

        // 3. 校验状态
        if (user.getStatus() != 1) {
            throw new AuthException(ErrorCode.AUTH_DISABLED, "账号已被禁用");
        }

        // 4. sa-token 登录
        StpUtil.login(user.getId());

        // 5. 更新最后登录时间
        sysUserMapper.updateLastLoginTime(user.getId(), LocalDateTime.now());

        return AuthResult.builder()
            .accessToken(StpUtil.getTokenValue())
            .tokenType("Bearer")
            .expiresIn(604800)
            .userId(user.getId())
            .build();
    }

    @Override
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否存在
        SysUser existingUser = sysUserMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户名已存在");
        }

        // 2. 创建用户
        SysUser user = SysUser.builder()
            .id(UUID.randomUUID().toString())
            .username(request.getUsername())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .nickname(request.getNickname())
            .email(request.getEmail())
            .role("USER")
            .status(1)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        sysUserMapper.insert(user);
        log.info("用户注册成功: {}", user.getUsername());
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}