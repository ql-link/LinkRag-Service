package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.model.dto.response.DocumentFileCapabilitiesDTO;
import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.DocumentFileService;
import com.qingluo.link.service.DocumentParseTaskService;
import com.qingluo.link.service.document.DocumentFileUploadCommand;
import java.util.Arrays;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "知识文件接口", description = "知识文件上传、Markdown 配套图片导入、解析任务和结果查询")
public class DocumentFileController {

    private final DocumentFileService documentFileService;
    private final DocumentParseTaskService documentParseTaskService;

    @PostMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    @Operation(summary = "上传知识文件", description = "普通文件沿用单对象上传；Markdown 可附带图片、相对路径和目录清单组成资源包")
    public Result<DocumentFileDTO> upload(
                                           @Parameter(description = "目标数据集ID", example = "10001")
                                           @PathVariable Long datasetId,
                                           @Parameter(description = "待上传文档文件")
                                           @RequestParam("file") MultipartFile file,
                                           @Parameter(description = "上传完成后是否立即解析", example = "true")
                                           @RequestParam(defaultValue = "false") boolean parseImmediately,
                                           @Parameter(description = "Markdown 图片匹配模式：FULL_PATH 完整相对路径，SHALLOW_BASENAME 一级文件名")
                                           @RequestParam(required = false) String matchMode,
                                           @Parameter(description = "文档在 ZIP 或文件夹虚拟树中的相对路径", example = "docs/guide.md")
                                           @RequestParam(required = false) String documentPath,
                                           @Parameter(description = "实际命中的配套图片文件；顺序须与 assetRelativePaths 一致")
                                           @RequestParam(required = false) List<MultipartFile> assets,
                                           @Parameter(description = "配套图片在虚拟树中的规范相对路径")
                                           @RequestParam(required = false) List<String> assetRelativePaths,
                                           @Parameter(description = "虚拟树完整路径清单，用于权威判断缺失、歧义和不支持格式")
                                           @RequestParam(required = false) List<String> assetInventoryPaths) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.upload(userId, datasetId, new DocumentFileUploadCommand(
            file, parseImmediately, matchMode, documentPath, assets, assetRelativePaths, assetInventoryPaths)));
    }

    @GetMapping("/api/v1/document-file-capabilities")
    @SaCheckLogin
    @Operation(summary = "查询知识文件导入能力", description = "返回文档、图片和 ZIP 的动态限制及支持的 Markdown 匹配模式")
    public Result<DocumentFileCapabilitiesDTO> capabilities() {
        return Result.success(documentFileService.getCapabilities());
    }

    @GetMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    @Operation(summary = "分页查询数据集文件", description = "查询当前用户指定数据集中的知识文件")
    public Result<PageResult<DocumentFileDTO>> list(
                                                     @Parameter(description = "数据集ID", example = "10001") @PathVariable Long datasetId,
                                                     @Parameter(description = "上传状态筛选：UPLOADING、UPLOAD_SUCCESS、UPLOAD_FAILED") @RequestParam(required = false) String uploadStatus,
                                                     @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") int page,
                                                     @Parameter(description = "每页条数", example = "20") @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.list(userId, datasetId, uploadStatus, page, pageSize));
    }

    @GetMapping("/api/v1/files/recent")
    @SaCheckLogin
    @Operation(summary = "查询最近知识文件", description = "按创建时间倒序查询当前用户跨数据集的最近文件")
    public Result<PageResult<DocumentFileDTO>> recent(
                                                       @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") int page,
                                                       @Parameter(description = "每页条数", example = "5") @RequestParam(defaultValue = "5") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.listRecent(userId, page, pageSize));
    }

    @GetMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    @Operation(summary = "查询知识文件详情", description = "v1 Markdown 资源包会同时返回服务端图片匹配汇总")
    public Result<DocumentFileDTO> detail(
            @Parameter(description = "知识文件ID", example = "10001") @PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.detail(userId, fileId));
    }

    @PostMapping("/api/v1/files/{fileId}/parse")
    @SaCheckLogin
    @Operation(summary = "提交文件解析任务", description = "存在资源缺失时默认返回409；用户确认后可显式忽略并继续解析")
    public Result<FileParseSubmitDTO> createParseTask(
            @Parameter(description = "知识文件ID", example = "10001") @PathVariable Long fileId,
            @Parameter(description = "是否确认忽略缺失、歧义或不支持的图片", example = "false")
            @RequestParam(defaultValue = "false") boolean ignoreMissingAssets) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentParseTaskService.submitManualParse(userId, fileId, ignoreMissingAssets));
    }

    @DeleteMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    @Operation(summary = "删除知识文件", description = "软删除当前用户拥有的知识文件记录")
    public Result<Void> delete(
            @Parameter(description = "知识文件ID", example = "10001") @PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        documentFileService.delete(userId, fileId);
        return Result.ok(null);
    }

    @GetMapping("/api/v1/datasets/{datasetId}/files/parse-results")
    @SaCheckLogin
    @Operation(summary = "批量查询文件解析结果", description = "按文件ID集合查询前端状态、解析状态和 Markdown 图片汇总")
    public Result<List<FileParseResultDTO>> parseResults(
                                                          @Parameter(description = "数据集ID", example = "10001") @PathVariable Long datasetId,
                                                          @Parameter(description = "逗号分隔的文件ID", example = "10001,10002") @RequestParam String fileIds) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentParseTaskService.listParseResults(userId, datasetId, parseFileIds(fileIds)));
    }

    private List<Long> parseFileIds(String fileIds) {
        if (fileIds == null || fileIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fileIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(Long::valueOf)
            .toList();
    }
}
