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

/**
 * 本地 OSS 公共文件预览入口。
 *
 * <p>仅在本地存储模式下把 public 目录中的对象映射为可预览资源，用于开发和单机部署场景。
 */
@RestController
public class ApiLocalOssPreviewController {

    private static final String PUBLIC_ROUTE_PREFIX = "/api/v1/oss-files/public/";

    private final OssProperties ossProperties;

    /**
     * 创建本地公共文件预览 Controller。
     *
     * @param ossProperties OSS 配置，用于读取本地 public 文件根目录
     */
    public ApiLocalOssPreviewController(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    /**
     * 预览 public 目录下的本地对象文件。
     *
     * <p>接口会校验对象 key 是否仍位于 public 根目录内，避免通过路径穿越读取非公开文件。
     *
     * @param request HTTP 请求，用于从完整路径中解析对象 key
     * @return 文件流响应；对象不存在或 key 为空时返回 404
     * @throws IOException 文件路径解析或读取失败时抛出
     */
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

    /**
     * 从当前请求路径中提取 public 对象 key。
     *
     * @param request HTTP 请求
     * @return URL 解码后的对象 key；路径不匹配时返回空字符串
     */
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

    /**
     * 将对象 key 解析成本地 public 文件路径。
     *
     * <p>解析后路径必须仍位于 public 根目录内，这是本地预览接口的核心安全边界。
     *
     * @param objectKey public 对象 key
     * @return 规范化后的本地文件路径
     * @throws IOException 对象 key 试图逃逸 public 根目录时抛出
     */
    private Path resolvePublicPath(String objectKey) throws IOException {
        Path base = Path.of(ossProperties.getFilePublicPath()).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Illegal object key: " + objectKey);
        }
        return target;
    }

    /**
     * 根据本地文件推断响应媒体类型。
     *
     * @param file 本地文件路径
     * @return 可识别时返回具体媒体类型，否则降级为二进制流
     */
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
