package com.qingluo.link.service;

import com.qingluo.link.core.dto.response.DailyUsageDTO;
import com.qingluo.link.core.dto.response.PageResult;
import com.qingluo.link.core.dto.response.UsageLogDTO;
import com.qingluo.link.core.dto.response.UsageSummaryDTO;

import java.util.List;

/**
 * 用量查询服务接口
 */
public interface UsageQueryService {

    /**
     * 获取用户用量统计
     */
    UsageSummaryDTO getUsageSummary(String userId, String startDate, String endDate);

    /**
     * 获取用户日度用量统计
     */
    List<DailyUsageDTO> getDailyUsage(String userId, String startDate, String endDate);

    /**
     * 获取用量明细
     */
    PageResult<UsageLogDTO> getUsageLogs(String userId, String startDate, String endDate, int page, int pageSize);
}