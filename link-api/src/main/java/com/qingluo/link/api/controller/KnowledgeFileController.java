package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
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

@RestController
@RequiredArgsConstructor
public class KnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;
    private final KnowledgeParseTaskService knowledgeParseTaskService;
    private final KnowledgeParseSseService knowledgeParseSseService;

    @PostMapping({"/api/v1/datasets/{datasetId}/files", "/api/v1/datasets/{datasetId}/knowledge-files"})
    @SaCheckLogin
    public Result<KnowledgeFileDTO> upload(@PathVariable Long datasetId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "false") boolean parseImmediately) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.upload(userId, datasetId, file, parseImmediately));
    }

    @GetMapping({"/api/v1/datasets/{datasetId}/files", "/api/v1/datasets/{datasetId}/knowledge-files"})
    @SaCheckLogin
    public Result<PageResult<KnowledgeFileDTO>> list(@PathVariable Long datasetId,
                                                     @RequestParam(required = false) String uploadStatus,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.list(userId, datasetId, uploadStatus, page, pageSize));
    }

    @GetMapping({"/api/v1/files/{fileId}", "/api/v1/knowledge-files/{fileId}"})
    @SaCheckLogin
    public Result<KnowledgeFileDTO> detail(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.detail(userId, fileId));
    }

    @PostMapping({"/api/v1/files/{fileId}/parse", "/api/v1/knowledge-files/{fileId}/parse-tasks"})
    @SaCheckLogin
    public Result<FileParseSubmitDTO> createParseTask(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeParseTaskService.submitManualParse(userId, fileId));
    }

    @DeleteMapping({"/api/v1/files/{fileId}", "/api/v1/knowledge-files/{fileId}"})
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        knowledgeFileService.delete(userId, fileId);
        return Result.ok(null);
    }

    @GetMapping(value = "/api/v1/datasets/{datasetId}/files/parse-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public SseEmitter subscribeParseEvents(@PathVariable Long datasetId, @RequestParam String fileIds) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return knowledgeParseSseService.subscribe(userId, datasetId, parseFileIds(fileIds));
    }

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
