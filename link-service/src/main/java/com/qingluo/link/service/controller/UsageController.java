package com.qingluo.link.service.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.dto.response.*;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.service.UsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用量控制器
 */
@RestController
@RequestMapping("/api/v1/llm/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageQueryService usageQueryService;

    @SaCheckLogin
    @GetMapping("/summary")
    public Result<UsageSummaryDTO> getSummary(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        String userId = AuthContext.getCurrentUserId();
        UsageSummaryDTO result = usageQueryService.getUsageSummary(userId, startDate, endDate);
        return Result.success(result);
    }

    @SaCheckLogin
    @GetMapping("/daily")
    public Result<List<DailyUsageDTO>> getDailyUsage(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        String userId = AuthContext.getCurrentUserId();
        List<DailyUsageDTO> result = usageQueryService.getDailyUsage(userId, startDate, endDate);
        return Result.success(result);
    }

    @SaCheckLogin
    @GetMapping("/logs")
    public Result<PageResult<UsageLogDTO>> getUsageLogs(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = AuthContext.getCurrentUserId();
        PageResult<UsageLogDTO> result = usageQueryService.getUsageLogs(userId, startDate, endDate, page, pageSize);
        return Result.success(result);
    }
}