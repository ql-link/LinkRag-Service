package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.DatasetService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping
    @SaCheckLogin
    public Result<DatasetDTO> create(@Valid @RequestBody CreateDatasetRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.create(userId, request));
    }

    @GetMapping
    @SaCheckLogin
    public Result<PageResult<DatasetDTO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.list(userId, page, pageSize));
    }

    @GetMapping("/{datasetId}")
    @SaCheckLogin
    public Result<DatasetDTO> detail(@PathVariable Long datasetId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.detail(userId, datasetId));
    }

    @DeleteMapping("/{datasetId}")
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long datasetId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        datasetService.delete(userId, datasetId);
        return Result.ok(null);
    }
}
