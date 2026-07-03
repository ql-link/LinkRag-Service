package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import com.qingluo.link.model.dto.response.LogLabelsDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.AdminLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端日志查询代理。
 */
@RestController
@RequestMapping("/api/v1/admin/logs")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
@Tag(name = "管理员日志接口", description = "集中日志查询代理（需 ADMIN 角色）")
public class AdminLogController {

    private final AdminLogQueryService adminLogQueryService;

    @GetMapping
    @Operation(summary = "查询集中日志", description = "代理查询 Loki，支持按服务、级别、trace_id、关键词和时间范围过滤")
    public Result<PageResult<LogEntryDTO>> queryLogs(
            @Parameter(description = "服务名") @RequestParam(required = false) String service,
            @Parameter(description = "日志级别") @RequestParam(required = false) String level,
            @Parameter(description = "链路追踪ID") @RequestParam(name = "trace_id", required = false) String traceId,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "开始时间，ISO 格式") @RequestParam(name = "start_time", required = false) String startTime,
            @Parameter(description = "结束时间，ISO 格式") @RequestParam(name = "end_time", required = false) String endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
        return Result.success(adminLogQueryService.queryLogs(
            service, level, traceId, keyword, startTime, endTime, page, pageSize));
    }

    @GetMapping("/labels")
    @Operation(summary = "查询日志筛选标签", description = "返回可筛选服务和固定日志级别列表")
    public Result<LogLabelsDTO> labels() {
        return Result.success(adminLogQueryService.listLabels());
    }
}
