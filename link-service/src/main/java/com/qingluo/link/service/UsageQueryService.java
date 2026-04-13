package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.DailyUsageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UsageLogDTO;
import com.qingluo.link.model.dto.response.UsageSummaryDTO;
import java.util.List;

/**
 * 用量查询服务接口
 */
public interface UsageQueryService {

    /**
     * 获取用量汇总
     */
    UsageSummaryDTO getSummary(Long userId, String startDate, String endDate);

    /**
     * 获取日度用量
     */
    List<DailyUsageDTO> getDailyUsage(Long userId, String startDate, String endDate);

    /**
     * 获取用量明细
     */
    PageResult<UsageLogDTO> getUsageLogs(Long userId, String startDate, String endDate, int page, int pageSize);
}