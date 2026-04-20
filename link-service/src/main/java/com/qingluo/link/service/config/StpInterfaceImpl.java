package com.qingluo.link.service.config;

import cn.dev33.satoken.stp.StpInterface;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * sa-token 角色与权限加载实现
 * 优先读 user:info 缓存，缓存未命中时回源 DB 并写缓存
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;
    private final UserCacheService userCacheService;

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = (Long) loginId;

        UserProfileDTO cached = userCacheService.get(userId);
        if (cached != null) {
            return List.of(cached.getRole());
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }

        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        userCacheService.put(userId, dto);

        return List.of(user.getRole());
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
