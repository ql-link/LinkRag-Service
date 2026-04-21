package com.qingluo.link.api.controller;

import com.qingluo.link.components.oss.config.OssProperties;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalOssPreviewController {

    private static final String PUBLIC_ROUTE_PREFIX = "/api/v1/oss-files/public/";

    private final OssProperties ossProperties;

    public LocalOssPreviewController(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @GetMapping(PUBLIC_ROUTE_PREFIX + "**")
    public ResponseEntity<InputStreamResource> previewPublicFile(HttpServletRequest request) throws IOException {
        String objectKey = extractObjectKey(request);
        if (!StringUtils.hasText(objectKey)) {
            return ResponseEntity.notFound().build();
        }

        Path file = resolvePublicPath(objectKey);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = resolveMediaType(file);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(Files.size(file))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
            .body(new InputStreamResource(Files.newInputStream(file)));
    }

    private String extractObjectKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith(PUBLIC_ROUTE_PREFIX)) {
            return "";
        }
        String objectKey = path.substring(PUBLIC_ROUTE_PREFIX.length());
        return URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
    }

    private Path resolvePublicPath(String objectKey) throws IOException {
        Path base = Path.of(ossProperties.getFilePublicPath()).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Illegal object key: " + objectKey);
        }
        return target;
    }

    private MediaType resolveMediaType(Path file) {
        try {
            String contentType = Files.probeContentType(file);
            if (StringUtils.hasText(contentType)) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
