package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.NotFoundException;
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

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper sysUserMapper;
    private final UserCacheService userCacheService;

    @Override
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
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw NotFoundException.userNotFound();
        }
        user.setStatus(request.getStatus());
        sysUserMapper.updateById(user);
        userCacheService.evict(userId);
    }

    @Override
    public void updateUserRole(Long userId, UpdateUserRoleRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw NotFoundException.userNotFound();
        }
        UserRole.of(request.getRole());
        user.setRole(request.getRole());
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
