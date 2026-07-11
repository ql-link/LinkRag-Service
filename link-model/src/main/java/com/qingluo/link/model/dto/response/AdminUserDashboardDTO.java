package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端用户统计看板")
public class AdminUserDashboardDTO {
    private int rangeDays;
    private long totalUsers;
    private AdminUserCountBreakdownDTO breakdown;
    private AdminUserPeriodMetricDTO newUsers;
    private AdminUserPeriodMetricDTO activeUsers;
    private List<AdminUserTrendPointDTO> trend;
}
