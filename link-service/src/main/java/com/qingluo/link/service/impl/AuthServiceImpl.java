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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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
        String username = normalizeUsername(request.getUsername());
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
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
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        userCacheService.put(user.getId(), toDTO(user));

        return new AuthResult(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout(), user.getId());
    }

    @Override
    public AuthResult register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        SysUser existUser = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );
        if (existUser != null) {
            throw new ConflictException(ErrorCode.DUPLICATE_USERNAME);
        }

        String email = normalizeOptional(request.getEmail());
        if (email != null) {
            SysUser existEmailUser = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email)
            );
            if (existEmailUser != null) {
                throw new ConflictException(ErrorCode.DUPLICATE_EMAIL);
            }
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(resolveNickname(request.getNickname(), username));
        user.setEmail(email);
        user.setRole(UserRole.USER.name());
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());

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

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveNickname(String nickname, String username) {
        String normalizedNickname = normalizeOptional(nickname);
        return normalizedNickname != null ? normalizedNickname : username;
    }
}
