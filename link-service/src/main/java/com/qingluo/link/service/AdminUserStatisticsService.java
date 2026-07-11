package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.AdminUserDashboardDTO;

public interface AdminUserStatisticsService {
    AdminUserDashboardDTO getDashboard(int days);
}
