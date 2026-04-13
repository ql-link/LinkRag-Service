package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.model.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llm/usage")
@RequiredArgsConstructor
public class UsageController {

    @GetMapping("/summary")
    @SaCheckLogin
    public Result<UsageSummaryDTO> getSummary(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        // TODO: 调用 UsageQueryService.getSummary()
        return Result.success(new UsageSummaryDTO());
    }

    @GetMapping("/daily")
    @SaCheckLogin
    public Result<List<DailyUsageDTO>> getDailyUsage(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        // TODO: 调用 UsageQueryService.getDailyUsage()
        return Result.success(List.of());
    }

    @GetMapping("/logs")
    @SaCheckLogin
    public Result<PageResult<UsageLogDTO>> getUsageLogs(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // TODO: 调用 UsageQueryService.getUsageLogs()
        return Result.success(new PageResult<>(List.of(), 0, page, pageSize));
    }
}