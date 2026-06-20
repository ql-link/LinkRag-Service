package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.*;
import com.qingluo.link.service.UsageQueryService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用量查询控制器
 * <p>提供 LLM 使用量的汇总、日报、调用日志查询功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/llm/usage")
@RequiredArgsConstructor
@Tag(name = "用量统计接口", description = "LLM使用量的汇总、日度统计、明细查询")
public class UsageController {

    private final UsageQueryService usageQueryService;

    /**
     * 获取用量汇总
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 用量汇总（totalCalls, totalTokens, promptTokens, completionTokens, averageLatencyMs）
     */
    @GetMapping("/summary")
    @SaCheckLogin
    @Operation(summary = "获取用量汇总", description = "获取指定时间范围内的用量汇总数据")
    public Result<UsageSummaryDTO> getSummary(
            @Parameter(description = "开始日期，格式yyyy-MM-dd", example = "2026-04-01")
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", example = "2026-04-15")
            @RequestParam String endDate,
            @Parameter(description = "阶段过滤：缺省仅统计对话(chat)，all 统计全链路，或指定 parse/recall", example = "chat")
            @RequestParam(defaultValue = "chat") String stage) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getSummary(userId, startDate, endDate, stage));
    }

    /**
     * 获取日度用量
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 日度用量列表
     */
    @GetMapping("/daily")
    @SaCheckLogin
    @Operation(summary = "获取日度用量", description = "按天统计用量数据，便于查看使用趋势")
    public Result<List<DailyUsageDTO>> getDailyUsage(
            @Parameter(description = "开始日期，格式yyyy-MM-dd", example = "2026-04-01")
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", example = "2026-04-15")
            @RequestParam String endDate,
            @Parameter(description = "阶段过滤：缺省仅统计对话(chat)，all 统计全链路，或指定 parse/recall", example = "chat")
            @RequestParam(defaultValue = "chat") String stage) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getDailyUsage(userId, startDate, endDate, stage));
    }

    /**
     * 获取用量明细
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @param page      页码（默认1）
     * @param pageSize  每页条数（默认20）
     * @return 用量明细列表
     */
    @GetMapping("/logs")
    @SaCheckLogin
    @Operation(summary = "获取用量明细", description = "获取每次LLM调用的详细记录")
    public Result<PageResult<UsageLogDTO>> getUsageLogs(
            @Parameter(description = "开始日期，格式yyyy-MM-dd", example = "2026-04-01")
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", example = "2026-04-15")
            @RequestParam String endDate,
            @Parameter(description = "阶段过滤：缺省仅统计对话(chat)，all 统计全链路，或指定 parse/recall", example = "chat")
            @RequestParam(defaultValue = "chat") String stage,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getUsageLogs(userId, startDate, endDate, stage, page, pageSize));
    }

    /**
     * 按模型聚合用量
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 按「厂商+模型」聚合的用量列表，按总 Token 降序
     */
    @GetMapping("/by-model")
    @SaCheckLogin
    @Operation(summary = "按模型聚合用量", description = "按厂商+模型聚合调用次数与 Token，按总 Token 降序（全链路口径）")
    public Result<List<ModelUsageDTO>> getUsageByModel(
            @Parameter(description = "开始日期，格式yyyy-MM-dd", example = "2026-04-01")
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", example = "2026-04-15")
            @RequestParam String endDate) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getUsageByModel(userId, startDate, endDate));
    }

    /**
     * 用量环比趋势
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 当前周期 vs 等长上一周期的环比增长率
     */
    @GetMapping("/trend")
    @SaCheckLogin
    @Operation(summary = "用量环比趋势", description = "当前周期与等长上一周期对比的 Token / 调用次数环比增长率（全链路口径）")
    public Result<UsageTrendDTO> getUsageTrend(
            @Parameter(description = "开始日期，格式yyyy-MM-dd", example = "2026-04-01")
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", example = "2026-04-15")
            @RequestParam String endDate) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(usageQueryService.getUsageTrend(userId, startDate, endDate));
    }
}
