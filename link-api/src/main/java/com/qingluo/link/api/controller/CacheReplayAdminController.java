package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.CacheReplayEventDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.cache.replay.CacheReplayEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/cache-replay-events")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
@Tag(name = "缓存补偿重放", description = "查询和处理 CDC/缓存补偿失败事实")
public class CacheReplayAdminController {

    private final CacheReplayEventService service;

    @GetMapping
    @Operation(summary = "分页查询缓存补偿失败事实")
    public Result<PageResult<CacheReplayEventDTO>> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(status, page, size));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "重放缓存补偿失败事实")
    public Result<CacheReplayEventDTO> replay(@PathVariable Long id) {
        return Result.success(service.replay(id, AuthContext.getLoginUserIdOrThrow()));
    }

    @PostMapping("/{id}/ignore")
    @Operation(summary = "忽略已人工补偿的失败事实")
    public Result<CacheReplayEventDTO> ignore(@PathVariable Long id) {
        return Result.success(service.ignore(id, AuthContext.getLoginUserIdOrThrow()));
    }
}
