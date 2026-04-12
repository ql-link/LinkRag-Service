package com.qingluo.link.service.impl;

import com.qingluo.link.core.dto.response.DailyUsageDTO;
import com.qingluo.link.core.dto.response.PageResult;
import com.qingluo.link.core.dto.response.UsageLogDTO;
import com.qingluo.link.core.dto.response.UsageSummaryDTO;
import com.qingluo.link.service.UsageQueryService;
import com.qingluo.link.service.mapper.UsageLogMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用量查询服务实现
 */
@Service
@RequiredArgsConstructor
public class UsageQueryServiceImpl implements UsageQueryService {

    private final UsageLogMapper usageLogMapper;

    @Override
    public UsageSummaryDTO getUsageSummary(String userId, String startDate, String endDate) {
        return usageLogMapper.selectSummaryByUserId(userId, startDate, endDate);
    }

    @Override
    public List<DailyUsageDTO> getDailyUsage(String userId, String startDate, String endDate) {
        return usageLogMapper.selectDailyUsageByUserId(userId, startDate, endDate);
    }

    @Override
    public PageResult<UsageLogDTO> getUsageLogs(String userId, String startDate, String endDate, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<UsageLogDTO> logs = usageLogMapper.selectLogsByUserId(userId, startDate, endDate);
        PageInfo<UsageLogDTO> pageInfo = new PageInfo<>(logs);

        return PageResult.<UsageLogDTO>builder()
            .items(pageInfo.getList())
            .total(pageInfo.getTotal())
            .page(page)
            .pageSize(pageSize)
            .build();
    }
}