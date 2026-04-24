package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.KnowledgeFileService;
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
public class KnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;

    @PostMapping("/api/v1/datasets/{datasetId}/knowledge-files")
    @SaCheckLogin
    public Result<KnowledgeFileDTO> upload(@PathVariable Long datasetId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "false") boolean parseImmediately) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.upload(userId, datasetId, file, parseImmediately));
    }

    @GetMapping("/api/v1/datasets/{datasetId}/knowledge-files")
    @SaCheckLogin
    public Result<PageResult<KnowledgeFileDTO>> list(@PathVariable Long datasetId,
                                                     @RequestParam(required = false) String uploadStatus,
                                                     @RequestParam(required = false) String parseNoticeStatus,
                                                     @RequestParam(required = false) String parseStatus,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.list(
            userId, datasetId, uploadStatus, parseNoticeStatus, parseStatus, page, pageSize));
    }

    @GetMapping("/api/v1/knowledge-files/{fileId}")
    @SaCheckLogin
    public Result<KnowledgeFileDTO> detail(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.detail(userId, fileId));
    }

    @PostMapping("/api/v1/knowledge-files/{fileId}/parse-tasks")
    @SaCheckLogin
    public Result<KnowledgeFileDTO> createParseTask(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeFileService.createParseTask(userId, fileId));
    }

    @DeleteMapping("/api/v1/knowledge-files/{fileId}")
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        knowledgeFileService.delete(userId, fileId);
        return Result.ok(null);
    }
}
