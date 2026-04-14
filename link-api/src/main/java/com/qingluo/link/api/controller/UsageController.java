package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.*;
import com.qingluo.link.service.UsageQueryService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llm/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageQueryService usageQueryService;

    @GetMapping("/summary")
    @SaCheckLogin
    public Result<UsageSummaryDTO> getSummary(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getSummary(userId, startDate, endDate));
    }

    @GetMapping("/daily")
    @SaCheckLogin
    public Result<List<DailyUsageDTO>> getDailyUsage(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getDailyUsage(userId, startDate, endDate));
    }

    @GetMapping("/logs")
    @SaCheckLogin
    public Result<PageResult<UsageLogDTO>> getUsageLogs(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getUsageLogs(userId, startDate, endDate, page, pageSize));
    }
}
