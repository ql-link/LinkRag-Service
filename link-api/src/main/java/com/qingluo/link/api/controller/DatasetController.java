package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.request.UpdateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.DatasetService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库数据集管理接口。
 *
 * <p>数据集是文档文件的业务归属边界，文件上传、解析结果和后续问答都按数据集隔离。
 */
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    /**
     * 创建当前用户的数据集。
     *
     * @param request 数据集名称、描述等创建参数
     * @return 创建后的数据集信息
     */
    @PostMapping
    @SaCheckLogin
    public Result<DatasetDTO> create(@Valid @RequestBody CreateDatasetRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.create(userId, request));
    }

    /**
     * 分页查询当前用户的数据集列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 当前用户可见的数据集分页结果
     */
    @GetMapping
    @SaCheckLogin
    public Result<PageResult<DatasetDTO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.list(userId, page, pageSize));
    }

    /**
     * 查询单个数据集详情。
     *
     * <p>Service 层会校验数据集归属，避免用户越权访问他人的数据集。
     *
     * @param datasetId 数据集 ID
     * @return 数据集详情
     */
    @GetMapping("/{datasetId}")
    @SaCheckLogin
    public Result<DatasetDTO> detail(@PathVariable Long datasetId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.detail(userId, datasetId));
    }

    /**
     * 更新当前用户的数据集基础信息。
     *
     * <p>Service 层会校验数据集归属，并处理同用户同名数据集冲突。
     *
     * @param datasetId 数据集 ID
     * @param request 数据集名称、描述等更新参数
     * @return 更新后的数据集信息
     */
    @PatchMapping("/{datasetId}")
    @SaCheckLogin
    public Result<DatasetDTO> update(@PathVariable Long datasetId,
                                     @Valid @RequestBody UpdateDatasetRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(datasetService.update(userId, datasetId, request));
    }

    /**
     * 删除当前用户的数据集。
     *
     * <p>删除数据集时会由 Service 层处理其下文件和解析数据的业务清理规则。
     *
     * @param datasetId 数据集 ID
     * @return 无返回内容
     */
    @DeleteMapping("/{datasetId}")
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long datasetId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        datasetService.delete(userId, datasetId);
        return Result.ok(null);
    }
}
