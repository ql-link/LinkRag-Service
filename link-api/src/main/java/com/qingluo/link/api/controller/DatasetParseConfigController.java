package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.DatasetParseConfigService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集级解析/检索配置管理接口。
 *
 * <p>承接前端配置页（LINK-150）：按数据集存取解析（分块/增强/PDF）与召回参数。Java 只做存/改/回显，
 * 默认值兜底与消费读取由 Python 在解析/召回时负责。
 */
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetParseConfigController {

    private final DatasetParseConfigService datasetParseConfigService;

    /**
     * 读取数据集解析/检索配置（回显已存；无配置返回四类空对象）。
     *
     * @param datasetId 数据集 ID
     * @return 已存配置
     */
    @GetMapping("/{datasetId}/parse-config")
    @SaCheckLogin
    public Result<DatasetParseConfigResponse> getConfig(@PathVariable Long datasetId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetParseConfigService.getConfig(userId, datasetId));
    }

    /**
     * 全量更新数据集解析/检索配置（整页保存，整行四类覆盖）。
     *
     * @param datasetId 数据集 ID
     * @param request 四类配置
     * @return 更新后的配置
     */
    @PutMapping("/{datasetId}/parse-config")
    @SaCheckLogin
    public Result<DatasetParseConfigResponse> updateConfig(@PathVariable Long datasetId,
                                                           @Valid @RequestBody UpdateDatasetParseConfigRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetParseConfigService.updateConfig(userId, datasetId, request));
    }
}
