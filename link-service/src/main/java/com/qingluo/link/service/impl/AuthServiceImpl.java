package com.qingluo.link.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.core.exception.AuthException;
import com.qingluo.link.core.exception.ConflictException;
import com.qingluo.link.core.log.AuditLog;
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
import com.qingluo.link.service.OssApplicationService;
import com.qingluo.link.service.SystemPresetService;
import com.qingluo.link.service.cache.UserCacheService;
import com.qingluo.link.service.oss.UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
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
    private final SystemPresetService systemPresetService;
    private final OssApplicationService ossApplicationService;

    /**
     * 校验账号密码并创建登录态，成功后同步刷新最后登录时间。
     */
    @Override
    public AuthResult login(LoginRequest request) {
        String account = normalizeRequired(request.getAccount());
        SysUser user = sysUserMapper.selectByAccount(account);

        if (user == null) {
            AuditLog.event("LOGIN_FAIL", "account={}, reason=USER_NOT_FOUND", account);
            throw AuthException.userNotFound();
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            AuditLog.event("LOGIN_FAIL", "userId={}, account={}, reason=INVALID_PASSWORD", user.getId(), account);
            throw AuthException.invalidPassword();
        }
        if (user.getStatus() != 1) {
            AuditLog.event("LOGIN_FAIL", "userId={}, account={}, reason=ACCOUNT_DISABLED", user.getId(), account);
            throw AuthException.accountDisabled();
        }

        StpUtil.login(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        userCacheService.put(user.getId(), toDTO(user));

        AuditLog.event("LOGIN_SUCCESS", "userId={}, account={}", user.getId(), account);
        return new AuthResult(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout(), user.getId());
    }

    /**
     * 注册新用户并自动登录，同时初始化最后登录时间。
     */
    @Override
    @Transactional
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
        // 注册即写入系统预设，实现开箱即用；失败则随事务回滚、阻断注册
        systemPresetService.applyPresetsForNewUser(user.getId());
        StpUtil.login(user.getId());
        userCacheService.put(user.getId(), toDTO(user));

        AuditLog.event("REGISTER", "userId={}, username={}", user.getId(), username);
        return new AuthResult(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout(), user.getId());
    }

    /**
     * 注销当前登录态。
     */
    @Override
    public void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        AuditLog.event("LOGOUT", "userId={}", loginId == null ? "-" : loginId);
    }

    /**
     * 获取当前用户资料，优先走缓存。
     */
    @Override
    public UserProfileDTO getProfile(Long userId) {
        return userCacheService.getOrLoad(userId, () -> {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw AuthException.userNotFound();
            }
            return toDTO(user);
        });
    }

    /**
     * 更新用户资料并清理缓存。
     */
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
     * 上传用户头像到公开 OSS，并将公开访问地址写回当前用户资料。
     */
    @Override
    @Transactional
    public UserProfileDTO uploadAvatar(Long userId, MultipartFile file) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw AuthException.userNotFound();
        }

        UploadResult uploadResult = ossApplicationService.uploadAndDescribe("avatar", file, buildAvatarObjectKey(userId, file));
        user.setAvatarUrl(uploadResult.previewUrl());
        sysUserMapper.updateById(user);
        userCacheService.evict(userId);
        return toDTO(user);
    }

    private String buildAvatarObjectKey(Long userId, MultipartFile file) {
        String suffix = getFileSuffix(file == null ? null : file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (StringUtils.hasText(suffix)) {
            return "avatar/" + userId + "/" + uuid + "." + suffix;
        }
        return "avatar/" + userId + "/" + uuid;
    }

    private String getFileSuffix(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
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
