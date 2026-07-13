package com.qingluo.link.service.config;

import cn.dev33.satoken.stp.StpInterface;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * sa-token 角色与权限加载实现，角色以 sys_user 为唯一事实来源。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId;
        try {
            userId = loginId instanceof Long ? (Long) loginId : Long.parseLong(String.valueOf(loginId));
        } catch (Exception e) {
            return Collections.emptyList();
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
