package com.qingluo.link.service.impl;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.core.exception.AuthException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.service.AuthService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResult login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null) {
            throw AuthException.userNotFound();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AuthException.invalidPassword();
        }

        if (user.getStatus() != 1) {
            throw AuthException.accountDisabled();
        }

        // 登录
        StpUtil.login(user.getId());

        return new AuthResult(
            StpUtil.getTokenValue(),
            "Bearer",
            StpUtil.getTokenTimeout(),
            user.getId()
        );
    }

    @Override
    public AuthResult register(RegisterRequest request) {
        // 检查用户名是否存在
        SysUser existUser = sysUserMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
        );

        if (existUser != null) {
            throw new com.qingluo.link.core.exception.ConflictException(
                com.qingluo.link.model.enums.ErrorCode.DUPLICATE_USER_CONFIG,
                "用户名已存在"
            );
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(1);

        sysUserMapper.insert(user);

        StpUtil.login(user.getId());

        return new AuthResult(
            StpUtil.getTokenValue(),
            "Bearer",
            StpUtil.getTokenTimeout(),
            user.getId()
        );
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}