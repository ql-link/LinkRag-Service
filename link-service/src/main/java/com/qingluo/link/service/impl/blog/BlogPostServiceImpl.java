package com.qingluo.link.service.impl.blog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.log.AuditLog;
import com.qingluo.link.mapper.BlogAssetMapper;
import com.qingluo.link.mapper.BlogPostMapper;
import com.qingluo.link.model.dto.entity.BlogAsset;
import com.qingluo.link.model.dto.entity.BlogPost;
import com.qingluo.link.model.dto.request.CreateBlogPostRequest;
import com.qingluo.link.model.dto.request.UpdateBlogPostRequest;
import com.qingluo.link.model.dto.response.BlogPostAdminDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostAdminListDTO;
import com.qingluo.link.model.dto.response.BlogPostPublicDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostPublicListDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.BlogAssetType;
import com.qingluo.link.model.enums.BlogPostStatus;
import com.qingluo.link.service.BlogContentStorageService;
import com.qingluo.link.service.BlogPostService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl implements BlogPostService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,98}[a-z0-9])$");

    private final BlogPostMapper blogPostMapper;
    private final BlogAssetMapper blogAssetMapper;
    private final BlogContentStorageService contentStorage;

    @Override
    public PageResult<BlogPostAdminListDTO> listAdmin(int page, int pageSize, String status) {
        LambdaQueryWrapper<BlogPost> query = new LambdaQueryWrapper<BlogPost>()
            .orderByDesc(BlogPost::getUpdatedAt)
            .orderByDesc(BlogPost::getId);
        if (StringUtils.hasText(status)) {
            query.eq(BlogPost::getStatus, parseStatus(status).name());
        }
        PageHelper.startPage(page, pageSize);
        List<BlogPost> posts = blogPostMapper.selectList(query);
        PageInfo<BlogPost> pageInfo = new PageInfo<>(posts);
        return new PageResult<>(posts.stream().map(this::toAdminListDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public BlogPostAdminDetailDTO detailAdmin(Long postId) {
        return toAdminDetailDTO(getActivePost(postId), true);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO create(Long operatorId, CreateBlogPostRequest request) {
        BlogPost post = new BlogPost();
        post.setTitle(requiredText(request.getTitle(), "文章标题不能为空"));
        post.setSlug(validateSlug(request.getSlug()));
        post.setSummary(trimToNull(request.getSummary()));
        post.setStatus(BlogPostStatus.DRAFT.name());
        post.setCreatedBy(operatorId);
        post.setIsDeleted(false);
        post.setDeletedSeq(0L);
        try {
            blogPostMapper.insert(post);
        } catch (DataIntegrityViolationException e) {
            throw badRequest("slug已存在");
        }
        AuditLog.event("BLOG_POST_CREATE", "operatorId={}, postId={}, slug={}",
            operatorId, post.getId(), post.getSlug());
        return toAdminDetailDTO(post, false);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO update(Long operatorId, Long postId, UpdateBlogPostRequest request) {
        BlogPost current = getActivePost(postId);
        BlogPost update = new BlogPost();
        update.setId(postId);
        boolean changed = false;

        if (request.getTitle() != null) {
            update.setTitle(requiredText(request.getTitle(), "文章标题不能为空"));
            changed = true;
        }
        if (request.getSlug() != null) {
            update.setSlug(validateSlug(request.getSlug()));
            changed = true;
        }
        if (request.getSummary() != null) {
            update.setSummary(trimToNull(request.getSummary()));
            changed = true;
        }
        if (request.getCoverAssetId() != null) {
            assertCoverAsset(postId, request.getCoverAssetId());
            update.setCoverAssetId(request.getCoverAssetId());
            changed = true;
        }
        if (!changed) {
            throw badRequest("请至少提供一个需要更新的字段");
        }

        try {
            blogPostMapper.updateById(update);
        } catch (DataIntegrityViolationException e) {
            throw badRequest("slug已存在");
        }
        AuditLog.event("BLOG_POST_UPDATE", "operatorId={}, postId={}", operatorId, current.getId());
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO uploadContent(Long operatorId, Long postId, MultipartFile file) {
        getActivePost(postId);
        String objectKey = contentStorage.uploadMarkdown(postId, file);
        BlogPost update = new BlogPost();
        update.setId(postId);
        update.setContentObjectKey(objectKey);
        blogPostMapper.updateById(update);
        AuditLog.event("BLOG_POST_CONTENT_UPLOAD", "operatorId={}, postId={}, objectKey={}",
            operatorId, postId, objectKey);
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO publish(Long operatorId, Long postId) {
        BlogPost post = getActivePost(postId);
        if (!StringUtils.hasText(post.getContentObjectKey())) {
            throw badRequest("请先上传Markdown正文");
        }
        if (!contentStorage.existsMarkdown(post.getContentObjectKey())) {
            throw badRequest("Markdown正文对象不存在");
        }

        BlogPost update = new BlogPost();
        update.setId(postId);
        update.setStatus(BlogPostStatus.PUBLISHED.name());
        if (post.getPublishedAt() == null) {
            update.setPublishedAt(LocalDateTime.now());
        }
        blogPostMapper.updateById(update);
        AuditLog.event("BLOG_POST_PUBLISH", "operatorId={}, postId={}", operatorId, postId);
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO unpublish(Long operatorId, Long postId) {
        getActivePost(postId);
        BlogPost update = new BlogPost();
        update.setId(postId);
        update.setStatus(BlogPostStatus.DRAFT.name());
        blogPostMapper.updateById(update);
        AuditLog.event("BLOG_POST_UNPUBLISH", "operatorId={}, postId={}", operatorId, postId);
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long postId) {
        BlogPost post = getActivePost(postId);
        blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
            .eq(BlogPost::getId, post.getId())
            .set(BlogPost::getIsDeleted, true)
            .set(BlogPost::getDeletedSeq, post.getId()));
        AuditLog.event("BLOG_POST_DELETE", "operatorId={}, postId={}, slug={}",
            operatorId, postId, post.getSlug());
    }

    @Override
    public PageResult<BlogPostPublicListDTO> listPublished(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<BlogPost> posts = blogPostMapper.selectList(new LambdaQueryWrapper<BlogPost>()
            .eq(BlogPost::getStatus, BlogPostStatus.PUBLISHED.name())
            .orderByDesc(BlogPost::getPublishedAt)
            .orderByDesc(BlogPost::getId));
        PageInfo<BlogPost> pageInfo = new PageInfo<>(posts);
        return new PageResult<>(posts.stream().map(this::toPublicListDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public BlogPostPublicDetailDTO publicDetail(String slug) {
        String normalizedSlug = validateSlug(slug);
        BlogPost post = blogPostMapper.selectOne(new LambdaQueryWrapper<BlogPost>()
            .eq(BlogPost::getSlug, normalizedSlug)
            .eq(BlogPost::getStatus, BlogPostStatus.PUBLISHED.name()));
        if (post == null) {
            throw new BusinessException(404, "博客文章不存在", 404);
        }
        BlogPostPublicDetailDTO dto = new BlogPostPublicDetailDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(post.getSummary());
        dto.setCoverAssetId(post.getCoverAssetId());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setContentMarkdown(contentStorage.readMarkdown(post.getContentObjectKey()));
        return dto;
    }

    private BlogPost getActivePost(Long postId) {
        BlogPost post = blogPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "博客文章不存在", 404);
        }
        return post;
    }

    private void assertCoverAsset(Long postId, Long assetId) {
        BlogAsset asset = blogAssetMapper.selectOne(new LambdaQueryWrapper<BlogAsset>()
            .eq(BlogAsset::getId, assetId)
            .eq(BlogAsset::getPostId, postId)
            .eq(BlogAsset::getAssetType, BlogAssetType.COVER.name()));
        if (asset == null) {
            throw badRequest("封面资源不存在或不属于当前文章");
        }
    }

    private BlogPostAdminListDTO toAdminListDTO(BlogPost post) {
        BlogPostAdminListDTO dto = new BlogPostAdminListDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(post.getSummary());
        dto.setContentObjectKey(post.getContentObjectKey());
        dto.setCoverAssetId(post.getCoverAssetId());
        dto.setStatus(post.getStatus());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedBy(post.getCreatedBy());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    private BlogPostAdminDetailDTO toAdminDetailDTO(BlogPost post, boolean includeContent) {
        BlogPostAdminDetailDTO dto = new BlogPostAdminDetailDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(post.getSummary());
        dto.setContentObjectKey(post.getContentObjectKey());
        dto.setCoverAssetId(post.getCoverAssetId());
        dto.setStatus(post.getStatus());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedBy(post.getCreatedBy());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        if (includeContent && StringUtils.hasText(post.getContentObjectKey())) {
            dto.setContentMarkdown(contentStorage.readMarkdown(post.getContentObjectKey()));
        }
        return dto;
    }

    private BlogPostPublicListDTO toPublicListDTO(BlogPost post) {
        BlogPostPublicListDTO dto = new BlogPostPublicListDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(post.getSummary());
        dto.setCoverAssetId(post.getCoverAssetId());
        dto.setPublishedAt(post.getPublishedAt());
        return dto;
    }

    private String validateSlug(String slug) {
        String normalized = requiredText(slug, "slug不能为空");
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw badRequest("slug格式不合法");
        }
        return normalized;
    }

    private BlogPostStatus parseStatus(String status) {
        try {
            return BlogPostStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw badRequest("文章状态不支持");
        }
    }

    private String requiredText(String value, String message) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            throw badRequest(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(40001, message, 400);
    }
}
