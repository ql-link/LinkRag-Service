package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.request.KnowledgeParseCallbackRequest;
import com.qingluo.link.service.KnowledgeFileDownloadResource;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.KnowledgeParseSseService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 原文件内部下载接口。
 *
 * <p>该 Controller 不面向浏览器用户直接调用，只服务于后续 Python 解析端读取原文件。
 * 因为原文件保存在私有 MinIO 桶中，解析端不能绕过 Java 端鉴权直接拼接对象地址。
 *
 * <p>一期先保留服务间下载能力；二期接入 MQ 后，Python 端可使用 MQ 消息中的 fileId
 * 和内部服务 Token 调用该接口获取原文件内容。
 */
@RestController
@RequiredArgsConstructor
public class InternalKnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;
    private final KnowledgeParseSseService knowledgeParseSseService;
    private final KnowledgeFileProperties properties;

    /**
     * 下载指定原文件内容。
     *
     * <p>这里使用 Bearer 服务 Token，而不是用户登录态：
     * 用户登录态只适合前端访问，Python 解析端属于后端服务间调用。
     */
    @GetMapping("/api/v1/internal/files/{fileId}/content")
    public ResponseEntity<?> download(@PathVariable Long fileId,
                                      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        // 内部下载接口只给服务间调用，不能复用用户登录态；二期 Python 解析也应通过服务 Token 取原文件。
        if (!isServiceTokenValid(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "服务鉴权失败"));
        }

        KnowledgeFileDownloadResource download = knowledgeFileService.openOriginalFile(fileId);
        Resource resource = new FileSystemResource(download.getFile());
        String contentType = StringUtils.hasText(download.getContentType())
            ? download.getContentType()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String encodedFilename = URLEncoder.encode(download.getOriginalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
            .body(resource);
    }

    /**
     * 接收 Python 解析事件并推送给浏览器 SSE。
     *
     * <p>Python 仍然负责写数据库最终状态；Java 这里只做内部鉴权、参数校验和进度/结果事件转发。
     */
    @PostMapping("/api/v1/internal/parse-tasks/{taskId}/events")
    public ResponseEntity<Result<Void>> parseEvent(@PathVariable String taskId,
                                                   @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                   @Valid @RequestBody KnowledgeParseCallbackRequest request) {
        if (!isServiceTokenValid(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "服务鉴权失败"));
        }
        if (request.getProgress() != null && (request.getProgress() < 0 || request.getProgress() > 100)) {
            return ResponseEntity.badRequest().body(Result.error(400, "解析进度必须在 0 到 100 之间"));
        }
        knowledgeParseSseService.publishTaskEvent(taskId, request);
        return ResponseEntity.ok(Result.ok(null));
    }

    /**
     * 校验内部服务 Token。
     *
     * <p>Token 必须放在 Authorization Header 中，避免出现在 URL、访问日志或浏览器历史记录里。
     */
    private boolean isServiceTokenValid(String authorization) {
        String expected = properties.getServiceToken();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(authorization)) {
            return false;
        }
        // 保持标准 Bearer 形式，避免把服务 Token 暴露到 URL query 或日志里。
        return authorization.equals("Bearer " + expected);
    }
}
