package com.qingluo.link.model.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 管理员修改用户状态请求
 */
@Data
public class UpdateUserStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
