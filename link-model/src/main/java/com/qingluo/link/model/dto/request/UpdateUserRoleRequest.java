package com.qingluo.link.model.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 管理员修改用户角色请求
 */
@Data
public class UpdateUserRoleRequest {

    @NotBlank(message = "角色不能为空")
    private String role;
}
