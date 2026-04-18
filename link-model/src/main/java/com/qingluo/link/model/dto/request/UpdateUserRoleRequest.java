package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 管理员修改用户角色请求
 */
@Data
@Schema(description = "修改用户角色请求")
public class UpdateUserRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Schema(description = "用户角色", example = "USER")
    private String role;
}
