package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端用户角色与状态分布")
public class AdminUserCountBreakdownDTO {
    private long user;
    private long admin;
    private long enabled;
    private long disabled;
}
