package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;

/**
 * 管理员用户管理服务接口
 */
public interface AdminUserService {

    /**
     * 分页查询用户列表
     */
    PageResult<UserProfileDTO> listUsers(int page, int size);

    /**
     * 修改用户状态（启用/禁用）
     */
    void updateUserStatus(Long userId, UpdateUserStatusRequest request);

    /**
     * 修改用户角色
     */
    void updateUserRole(Long userId, UpdateUserRoleRequest request);
}
