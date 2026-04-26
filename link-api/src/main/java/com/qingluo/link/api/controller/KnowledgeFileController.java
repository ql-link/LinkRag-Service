package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.KnowledgeParseSseService;
import com.qingluo.link.service.KnowledgeParseTaskService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识库原文件管理接口。
 *
 * <p>一期只负责“原文件上传到 MinIO 并记录上传事实”这一条链路：
 * 上传、列表、详情、删除都围绕 document_original_file 表工作。
 *
 * <p>这里暂时不触发解析、不创建解析任务、不返回解析产物。
 * 前端传入的 parseImmediately 仅用于保持二期交互形态兼容，真正的 MQ 投递和 Python 解析会在二期接入。
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;
    private final KnowledgeParseTaskService knowledgeParseTaskService;
    private final KnowledgeParseSseService knowledgeParseSseService;

    /**
     * 上传数据集原文件。
     *
     * <p>同一用户、同一数据集下，文件名和后缀相同即认为是同一份原文件。
     * 幂等与失败重试规则由 Service 层结合唯一索引和上传状态统一处理。
     */
    @PostMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    public Result<KnowledgeFileDTO> upload(@PathVariable Long datasetId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "false") boolean parseImmediately) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.upload(userId, datasetId, file, parseImmediately));
    }

    /**
     * 分页查询数据集下的原文件列表。
     *
     * <p>uploadStatus 只表示原文件上传状态，可选值由一期约定为 uploading、success、failed。
     * 解析状态不从这里返回，避免原文件表和后续解析任务表职责混在一起。
     */
    @GetMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    public Result<PageResult<KnowledgeFileDTO>> list(@PathVariable Long datasetId,
                                                     @RequestParam(required = false) String uploadStatus,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.list(userId, datasetId, uploadStatus, page, pageSize));
    }

    /**
     * 查询单个原文件详情。
     *
     * <p>详情接口会校验当前登录用户是否拥有该文件，避免通过 fileId 越权访问其他用户数据。
     */
    @GetMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    public Result<KnowledgeFileDTO> detail(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.detail(userId, fileId));
    }

    /**
     * 删除原文件。
     *
     * <p>删除动作会同时删除 MinIO 私有对象和数据库记录。
     * 如果对象删除失败则不删除数据库记录，保证前端仍能看到失败状态并重试或等待补偿。
     */
    @DeleteMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        knowledgeFileService.delete(userId, fileId);
        return Result.ok(null);
    }

    /**
     * 手动提交解析任务。
     *
     * <p>二期前端按文件维度订阅进度，不依赖 taskId 操作，所以这里只返回文件状态。
     */
    @PostMapping("/api/v1/files/{fileId}/parse")
    @SaCheckLogin
    public Result<FileParseSubmitDTO> parse(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeParseTaskService.submitManualParse(userId, fileId));
    }

    /**
     * 订阅本次文件列表的解析进度。
     *
     * <p>SSE 只做运行期推送；浏览器断开后可通过结果查询接口兜底。
     */
    @GetMapping(value = "/api/v1/datasets/{datasetId}/files/parse-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public SseEmitter subscribeParseEvents(@PathVariable Long datasetId,
                                           @RequestParam String fileIds) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return knowledgeParseSseService.subscribe(userId, datasetId, parseFileIds(fileIds));
    }

    /**
     * 查询本次文件列表的解析结果。
     *
     * <p>返回所有传入文件的当前结果，前端自行判断是否全部到达终态。
     */
    @GetMapping("/api/v1/datasets/{datasetId}/files/parse-results")
    @SaCheckLogin
    public Result<List<FileParseResultDTO>> parseResults(@PathVariable Long datasetId,
                                                         @RequestParam String fileIds) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeParseTaskService.listParseResults(userId, datasetId, parseFileIds(fileIds)));
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
