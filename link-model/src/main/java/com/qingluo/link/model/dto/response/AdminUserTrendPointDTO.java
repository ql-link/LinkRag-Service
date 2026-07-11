package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户新增与活跃日趋势")
public class AdminUserTrendPointDTO {
    private String date;
    private long newUsers;
    private long activeUsers;
}
