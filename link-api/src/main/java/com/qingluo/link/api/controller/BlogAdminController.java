package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateBlogPostRequest;
import com.qingluo.link.model.dto.request.SaveBlogContentRequest;
import com.qingluo.link.model.dto.request.UpdateBlogPostRequest;
import com.qingluo.link.model.dto.response.BlogAssetDTO;
import com.qingluo.link.model.dto.response.BlogPostAdminDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostAdminListDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.BlogAssetService;
import com.qingluo.link.service.BlogPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/blog")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
@Validated
@Tag(name = "博客管理接口", description = "博客文章与资源管理（需ADMIN角色）")
public class BlogAdminController {

    private final BlogPostService blogPostService;
    private final BlogAssetService blogAssetService;

    @GetMapping("/posts")
    @Operation(summary = "分页查询博客文章管理列表")
    public Result<PageResult<BlogPostAdminListDTO>> listPosts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String status) {
        return Result.success(blogPostService.listAdmin(page, pageSize, status));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "查询博客文章管理详情")
    public Result<BlogPostAdminDetailDTO> detail(@PathVariable Long postId) {
        return Result.success(blogPostService.detailAdmin(postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "创建博客草稿")
    public Result<BlogPostAdminDetailDTO> create(@Valid @RequestBody CreateBlogPostRequest request) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.create(operatorId, request));
    }

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "更新博客文章元数据")
    public Result<BlogPostAdminDetailDTO> update(@PathVariable Long postId,
                                                 @Valid @RequestBody UpdateBlogPostRequest request) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.update(operatorId, postId, request));
    }

    @PostMapping("/posts/{postId}/content/import")
    @Operation(summary = "导入博客Markdown草稿")
    public Result<BlogPostAdminDetailDTO> importContent(@PathVariable Long postId,
                                                        @RequestParam("file") MultipartFile file) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.importContent(operatorId, postId, file));
    }

    @PostMapping("/posts/{postId}/content")
    @Operation(summary = "导入博客Markdown草稿（兼容旧路径）")
    public Result<BlogPostAdminDetailDTO> importContentCompat(@PathVariable Long postId,
                                                              @RequestParam("file") MultipartFile file) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.importContent(operatorId, postId, file));
    }

    @PutMapping("/posts/{postId}/content")
    @Operation(summary = "保存博客Markdown正文")
    public Result<BlogPostAdminDetailDTO> saveContent(@PathVariable Long postId,
                                                      @Valid @RequestBody SaveBlogContentRequest request) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.saveContent(operatorId, postId, request));
    }

    @PostMapping("/posts/{postId}/publish")
    @Operation(summary = "发布博客文章")
    public Result<BlogPostAdminDetailDTO> publish(@PathVariable Long postId) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.publish(operatorId, postId));
    }

    @PostMapping("/posts/{postId}/unpublish")
    @Operation(summary = "下架博客文章")
    public Result<BlogPostAdminDetailDTO> unpublish(@PathVariable Long postId) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogPostService.unpublish(operatorId, postId));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "删除博客文章")
    public Result<Void> delete(@PathVariable Long postId) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        blogPostService.delete(operatorId, postId);
        return Result.success(null);
    }

    @GetMapping("/posts/{postId}/assets")
    @Operation(summary = "查询博客文章资源")
    public Result<List<BlogAssetDTO>> listAssets(@PathVariable Long postId,
                                                 @RequestParam(required = false) String assetType) {
        return Result.success(blogAssetService.list(postId, assetType));
    }

    @PostMapping("/posts/{postId}/assets")
    @Operation(summary = "上传博客文章封面图片")
    public Result<BlogAssetDTO> uploadAsset(@PathVariable Long postId,
                                            @RequestParam("assetType") String assetType,
                                            @RequestParam("file") MultipartFile file) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(blogAssetService.upload(operatorId, postId, assetType, file));
    }

    @DeleteMapping("/posts/{postId}/assets/{assetId}")
    @Operation(summary = "删除博客文章资源")
    public Result<Void> deleteAsset(@PathVariable Long postId, @PathVariable Long assetId) {
        Long operatorId = AuthContext.getLoginUserIdOrThrow();
        blogAssetService.delete(operatorId, postId, assetId);
        return Result.success(null);
    }
}
