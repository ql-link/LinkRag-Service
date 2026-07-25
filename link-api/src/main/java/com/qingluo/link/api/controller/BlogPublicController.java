package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.BlogPostPublicDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostPublicListDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.BlogPostService;
import com.qingluo.link.service.blog.BlogPublicDetailSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/blog")
@RequiredArgsConstructor
@Tag(name = "公开博客接口", description = "公开读取已发布博客文章")
public class BlogPublicController {

    private final BlogPostService blogPostService;

    @GetMapping("/posts")
    @Operation(summary = "分页查询公开博客文章列表")
    public Result<PageResult<BlogPostPublicListDTO>> listPublished(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(blogPostService.listPublished(page, pageSize));
    }

    @GetMapping("/posts/{slug}")
    @Operation(summary = "查询公开博客文章详情")
    public ResponseEntity<Result<BlogPostPublicDetailDTO>> detail(
        @PathVariable String slug,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        BlogPublicDetailSnapshot snapshot = blogPostService.resolvePublicDetail(slug);
        if (matches(ifNoneMatch, snapshot.etag())) {
            return ResponseEntity.status(304)
                .eTag(snapshot.etag())
                .header(HttpHeaders.CACHE_CONTROL, "public, no-cache")
                .build();
        }
        return ResponseEntity.ok()
            .eTag(snapshot.etag())
            .header(HttpHeaders.CACHE_CONTROL, "public, no-cache")
            .body(Result.success(blogPostService.publicDetail(snapshot)));
    }

    private boolean matches(String ifNoneMatch, String current) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String normalizedCurrent = weakValue(current);
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed) || normalizedCurrent.equals(weakValue(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private String weakValue(String etag) {
        String value = etag == null ? "" : etag.trim();
        return value.startsWith("W/") ? value.substring(2) : value;
    }
}
