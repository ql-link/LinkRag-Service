package com.qingluo.link.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.AuthException;
import com.qingluo.link.core.exception.ConflictException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.request.UpdateProfileRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.UserRole;
import com.qingluo.link.service.AuthService;
import com.qingluo.link.service.cache.UserCacheService;
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
    private final UserCacheService userCacheService;

    @Override
    public AuthResult login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername())
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

        StpUtil.login(user.getId());
        userCacheService.put(user.getId(), toDTO(user));

        return new AuthResult(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout(), user.getId());
    }

    @Override
    public AuthResult register(RegisterRequest request) {
        SysUser existUser = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername())
        );
        if (existUser != null) {
            throw new ConflictException(ErrorCode.DUPLICATE_USER_CONFIG, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER.name());
        user.setStatus(1);

        sysUserMapper.insert(user);
        StpUtil.login(user.getId());
        userCacheService.put(user.getId(), toDTO(user));

        return new AuthResult(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout(), user.getId());
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserProfileDTO getProfile(Long userId) {
        UserProfileDTO cached = userCacheService.get(userId);
        if (cached != null) {
            return cached;
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw AuthException.userNotFound();
        }

        UserProfileDTO dto = toDTO(user);
        userCacheService.put(userId, dto);
        return dto;
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw AuthException.userNotFound();
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        sysUserMapper.updateById(user);
        userCacheService.evict(userId);
    }

    private UserProfileDTO toDTO(SysUser user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
