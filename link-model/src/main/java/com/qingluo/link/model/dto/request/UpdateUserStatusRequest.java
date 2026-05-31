package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 管理员修改用户状态请求
 */
@Data
@Schema(description = "修改用户状态请求")
public class UpdateUserStatusRequest {

    @NotNull(message = "状态不能为空")
    @Schema(description = "用户状态 0-禁用 1-启用", example = "1")
    private Integer status;
}
