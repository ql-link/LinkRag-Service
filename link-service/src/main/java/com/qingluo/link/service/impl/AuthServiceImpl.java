package com.qingluo.link.service.impl;

import cn.dev33.satoken.stp.StpUtil;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserCacheService userCacheService;

    /**
     * 认证链路同时承接注册、登录和资料修改，因此把账号归一化和默认昵称生成集中在这里，
     * 避免不同入口各自维护一套规则后再次出现字段语义漂移。
     */
    @Override
    /**
     * 校验账号密码并创建登录态。
     */
    public AuthResult login(LoginRequest request) {
        String account = normalizeRequired(request.getAccount());
        SysUser user = sysUserMapper.selectByAccount(account);

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
    /**
     * 注册新用户并自动登录。
     */
    public AuthResult register(RegisterRequest request) {
        String username = normalizeRequired(request.getUsername());
        SysUser existUser = sysUserMapper.selectByUsername(username);
        if (existUser != null) {
            throw new ConflictException(ErrorCode.DUPLICATE_USERNAME);
        }

        String email = normalizeRequired(request.getEmail());
        SysUser existEmailUser = sysUserMapper.selectByEmail(email);
        if (existEmailUser != null) {
            throw new ConflictException(ErrorCode.DUPLICATE_EMAIL);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(generateDefaultNickname());
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
    /**
     * 注销当前登录态。
     */
    public void logout() {
        StpUtil.logout();
    }

    @Override
    /**
     * 获取当前用户资料，优先走缓存。
     */
    public UserProfileDTO getProfile(Long userId) {
        return userCacheService.getOrLoad(userId, () -> {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw AuthException.userNotFound();
            }
            return toDTO(user);
        });
    }

    @Override
    /**
     * 更新用户资料并清理缓存。
     */
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw AuthException.userNotFound();
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            String email = normalizeOptional(request.getEmail());
            if (email != null && !email.equals(user.getEmail())) {
                SysUser conflictUser = sysUserMapper.selectByEmailExcludingUserId(email, userId);
                if (conflictUser != null) {
                    throw new ConflictException(ErrorCode.DUPLICATE_EMAIL);
                }
            }
            user.setEmail(email);
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

    /**
     * 将用户实体转换为用户资料 DTO。
     */
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

    /**
     * 登录账号和注册主标识必须以去首尾空格后的值参与唯一性判断，
     * 否则同一个业务身份会因为前后空格出现伪差异。
     */
    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 默认昵称只承担展示职责，不要求和用户名或邮箱建立映射关系，
     * 这样后续即使产品调整展示策略，也不会反向影响稳定登录标识。
     */
    private String generateDefaultNickname() {
        return "用户" + randomAlphaNumeric(7);
    }

    private String randomAlphaNumeric(int length) {
        final char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        StringBuilder builder = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(chars[random.nextInt(chars.length)]);
        }
        return builder.toString();
    }
}
