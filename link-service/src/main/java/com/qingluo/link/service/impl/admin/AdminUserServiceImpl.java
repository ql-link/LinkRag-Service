package com.qingluo.link.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.log.AuditLog;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.model.enums.UserRole;
import com.qingluo.link.service.AdminUserService;
import com.qingluo.link.service.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端用户服务实现，负责用户列表查询和角色状态维护。
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper sysUserMapper;
    private final UserCacheService userCacheService;

    @Override
    /**
     * 分页查询用户列表并转换为管理端展示 DTO。
     */
    public PageResult<UserProfileDTO> listUsers(int page, int size) {
        Page<SysUser> pageParam = new Page<>(page, size);
        Page<SysUser> result = sysUserMapper.selectPage(pageParam, new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt));

        List<UserProfileDTO> items = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResult<>(items, result.getTotal(), page, size);
    }

    @Override
    /**
     * 更新用户状态并失效用户缓存。
     */
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw NotFoundException.userNotFound();
        }
        Integer oldStatus = user.getStatus();
        user.setStatus(request.getStatus());
        sysUserMapper.updateById(user);
        userCacheService.evict(userId);
        AuditLog.event("USER_STATUS_CHANGE", "operatorId={}, targetUserId={}, {}->{}",
                AuthContext.getCurrentUserId(), userId, oldStatus, request.getStatus());
    }

    @Override
    /**
     * 更新用户角色并失效用户缓存。
     */
    public void updateUserRole(Long userId, UpdateUserRoleRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw NotFoundException.userNotFound();
        }
        UserRole.of(request.getRole());
        String oldRole = user.getRole();
        user.setRole(request.getRole());
        sysUserMapper.updateById(user);
        userCacheService.evict(userId);
        AuditLog.event("USER_ROLE_CHANGE", "operatorId={}, targetUserId={}, {}->{}",
                AuthContext.getCurrentUserId(), userId, oldRole, request.getRole());
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
}
