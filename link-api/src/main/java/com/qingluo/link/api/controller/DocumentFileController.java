package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.DocumentFileService;
import com.qingluo.link.service.DocumentParseTaskService;
import java.util.Arrays;
import java.util.List;
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
public class DocumentFileController {

    private final DocumentFileService documentFileService;
    private final DocumentParseTaskService documentParseTaskService;

    @PostMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    public Result<DocumentFileDTO> upload(@PathVariable Long datasetId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "false") boolean parseImmediately) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.upload(userId, datasetId, file, parseImmediately));
    }

    @GetMapping("/api/v1/datasets/{datasetId}/files")
    @SaCheckLogin
    public Result<PageResult<DocumentFileDTO>> list(@PathVariable Long datasetId,
                                                     @RequestParam(required = false) String uploadStatus,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.list(userId, datasetId, uploadStatus, page, pageSize));
    }

    @GetMapping("/api/v1/files/recent")
    @SaCheckLogin
    public Result<PageResult<DocumentFileDTO>> recent(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "5") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.listRecent(userId, page, pageSize));
    }

    @GetMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    public Result<DocumentFileDTO> detail(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentFileService.detail(userId, fileId));
    }

    @PostMapping("/api/v1/files/{fileId}/parse")
    @SaCheckLogin
    public Result<FileParseSubmitDTO> createParseTask(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(documentParseTaskService.submitManualParse(userId, fileId));
    }

    @DeleteMapping("/api/v1/files/{fileId}")
    @SaCheckLogin
    public Result<Void> delete(@PathVariable Long fileId) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        documentFileService.delete(userId, fileId);
        return Result.ok(null);
    }

    @GetMapping("/api/v1/datasets/{datasetId}/files/parse-results")
    @SaCheckLogin
    public Result<List<FileParseResultDTO>> parseResults(@PathVariable Long datasetId,
                                                          @RequestParam String fileIds) {
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
