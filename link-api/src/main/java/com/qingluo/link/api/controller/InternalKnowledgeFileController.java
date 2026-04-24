package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.KnowledgeFileDownloadResource;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalKnowledgeFileController {

    private final KnowledgeFileService knowledgeFileService;
    private final KnowledgeFileProperties properties;

    @GetMapping("/api/v1/internal/knowledge-files/{fileId}/content")
    public ResponseEntity<?> download(@PathVariable Long fileId,
                                      @RequestParam String taskId,
                                      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!isServiceTokenValid(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "服务鉴权失败"));
        }

        KnowledgeFileDownloadResource download = knowledgeFileService.openOriginalFile(fileId, taskId);
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

    private boolean isServiceTokenValid(String authorization) {
        String expected = properties.getServiceToken();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(authorization)) {
            return false;
        }
        return authorization.equals("Bearer " + expected);
    }
}
