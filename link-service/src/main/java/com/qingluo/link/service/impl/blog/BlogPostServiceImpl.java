package com.qingluo.link.service.impl.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.observability.log.AuditLog;
import com.qingluo.link.mapper.BlogAssetMapper;
import com.qingluo.link.mapper.BlogPostMapper;
import com.qingluo.link.model.dto.entity.BlogAsset;
import com.qingluo.link.model.dto.entity.BlogPost;
import com.qingluo.link.model.dto.request.CreateBlogPostRequest;
import com.qingluo.link.model.dto.request.SaveBlogContentRequest;
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
import com.qingluo.link.service.blog.BlogPublicDetailSnapshot;
import com.qingluo.link.service.cache.BusinessCacheProperties;
import com.qingluo.link.service.cache.PublishedBlogIndexCache;
import com.qingluo.link.service.cache.PublishedBlogIndexSnapshot;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger log = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    private final BlogPostMapper blogPostMapper;
    private final BlogAssetMapper blogAssetMapper;
    private final BlogContentStorageService contentStorage;
    private final IOssService ossService;
    private final PublishedBlogIndexCache publishedBlogIndexCache;
    private final BusinessCacheProperties businessCacheProperties;
    private final BlogObjectCleanupScheduler cleanupScheduler;
    private final ObjectMapper objectMapper;

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
        BlogPost post = null;
        for (int i = 0; i < 3; i++) {
            post = new BlogPost();
            post.setTitle(requiredText(request.getTitle(), "文章标题不能为空"));
            post.setSlug(generateSlug());
            post.setSummary(trimToNull(request.getSummary()));
            post.setStatus(BlogPostStatus.DRAFT.name());
            post.setCreatedBy(operatorId);
            post.setIsDeleted(false);
            post.setDeletedSeq(0L);
            try {
                blogPostMapper.insert(post);
                break;
            } catch (DataIntegrityViolationException e) {
                if (i == 2) {
                    throw new BusinessException(50001, "博客slug生成冲突", 500);
                }
            }
        }
        AuditLog.event("BLOG_POST_CREATE", "operatorId={}, postId={}, slug={}",
            operatorId, post.getId(), post.getSlug());
        publishedBlogIndexCache.evict();
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

        blogPostMapper.updateById(update);
        publishedBlogIndexCache.evict();
        AuditLog.event("BLOG_POST_UPDATE", "operatorId={}, postId={}", operatorId, current.getId());
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO importContent(Long operatorId, Long postId, MultipartFile file) {
        BlogPost post = getActivePost(postId);
        String oldContentKey = post.getContentObjectKey();
        BlogContentStorageService.ProcessedMarkdown processed =
            contentStorage.importMarkdown(postId, file, knownPublicUrls(postId));
        insertAutoImageAssets(operatorId, postId, processed.images());
        BlogPost update = new BlogPost();
        update.setId(postId);
        update.setContentObjectKey(processed.objectKey());
        blogPostMapper.updateById(update);
        cleanupScheduler.afterCommitDelete(postId, oldContentKey);
        publishedBlogIndexCache.evict();
        AuditLog.event("BLOG_POST_CONTENT_IMPORT", "operatorId={}, postId={}, objectKey={}",
            operatorId, postId, processed.objectKey());
        BlogPostAdminDetailDTO detail = detailAdmin(postId);
        detail.setContentMarkdown(processed.contentMarkdown());
        return detail;
    }

    @Override
    @Transactional
    public BlogPostAdminDetailDTO saveContent(Long operatorId, Long postId, SaveBlogContentRequest request) {
        BlogPost post = getActivePost(postId);
        String oldContentKey = post.getContentObjectKey();
        BlogContentStorageService.ProcessedMarkdown processed =
            contentStorage.saveMarkdown(postId, request.getContentMarkdown(), knownPublicUrls(postId));
        insertAutoImageAssets(operatorId, postId, processed.images());
        BlogPost update = new BlogPost();
        update.setId(postId);
        update.setContentObjectKey(processed.objectKey());
        blogPostMapper.updateById(update);
        cleanupScheduler.afterCommitDelete(postId, oldContentKey);
        publishedBlogIndexCache.evict();
        AuditLog.event("BLOG_POST_CONTENT_SAVE", "operatorId={}, postId={}, objectKey={}",
            operatorId, postId, processed.objectKey());
        BlogPostAdminDetailDTO detail = detailAdmin(postId);
        detail.setContentMarkdown(processed.contentMarkdown());
        return detail;
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
        publishedBlogIndexCache.evict();
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
        publishedBlogIndexCache.evict();
        AuditLog.event("BLOG_POST_UNPUBLISH", "operatorId={}, postId={}", operatorId, postId);
        return detailAdmin(postId);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long postId) {
        BlogPost post = getActivePost(postId);
        // 收集该文章下未删除的资源（封面/插图），随文章一并清理
        List<BlogAsset> assets = blogAssetMapper.selectList(new LambdaQueryWrapper<BlogAsset>()
            .eq(BlogAsset::getPostId, postId));
        // 先软删资源记录，再软删文章本身，保证 DB 状态一致
        if (!assets.isEmpty()) {
            blogAssetMapper.delete(new LambdaQueryWrapper<BlogAsset>()
                .eq(BlogAsset::getPostId, postId));
        }
        blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
            .eq(BlogPost::getId, post.getId())
            .set(BlogPost::getIsDeleted, true)
            .set(BlogPost::getDeletedSeq, post.getId()));
        // DB 一致后，best-effort 物理清理 OSS 上的正文与资源对象（失败仅告警，不阻断删除）
        Set<String> objectKeys = assets.stream()
            .map(BlogAsset::getObjectKey)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (StringUtils.hasText(post.getContentObjectKey())) {
            objectKeys.add(post.getContentObjectKey());
        }
        cleanupScheduler.afterCommitDelete(postId, objectKeys);
        publishedBlogIndexCache.evict();
        AuditLog.event("BLOG_POST_DELETE", "operatorId={}, postId={}, slug={}",
            operatorId, postId, post.getSlug());
    }

    @Override
    public PageResult<BlogPostPublicListDTO> listPublished(int page, int pageSize) {
        int start = Math.max(0, (page - 1) * pageSize);
        int end = start + pageSize;
        int capacity = businessCacheProperties.getBlogPublishedIndexCapacity();
        if (page > 0 && pageSize > 0 && start < capacity && end <= capacity) {
            PublishedBlogIndexSnapshot snapshot = publishedBlogIndexCache.get(this::loadPublishedIndex);
            int sliceEnd = Math.min(end, snapshot.getItems().size());
            List<BlogPostPublicListDTO> items = start >= sliceEnd
                ? List.of()
                : snapshot.getItems().subList(start, sliceEnd);
            return new PageResult<>(List.copyOf(items), snapshot.getTotal(), page, pageSize);
        }
        return queryPublishedPage(page, pageSize);
    }

    private PublishedBlogIndexSnapshot loadPublishedIndex() {
        int capacity = businessCacheProperties.getBlogPublishedIndexCapacity();
        PageResult<BlogPostPublicListDTO> page = queryPublishedPage(1, capacity);
        return new PublishedBlogIndexSnapshot(page.getItems(), page.getTotal(), capacity);
    }

    private PageResult<BlogPostPublicListDTO> queryPublishedPage(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<BlogPost> posts = blogPostMapper.selectList(new LambdaQueryWrapper<BlogPost>()
            .eq(BlogPost::getStatus, BlogPostStatus.PUBLISHED.name())
            .orderByDesc(BlogPost::getPublishedAt)
            .orderByDesc(BlogPost::getId));
        PageInfo<BlogPost> pageInfo = new PageInfo<>(posts);
        Map<Long, String> coverUrlMap = batchLoadCoverUrls(posts);
        return new PageResult<>(posts.stream().map(p -> toPublicListDTO(p, coverUrlMap)).toList(),
            pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public BlogPublicDetailSnapshot resolvePublicDetail(String slug) {
        String normalizedSlug = validateSlug(slug);
        BlogPost post = blogPostMapper.selectOne(new LambdaQueryWrapper<BlogPost>()
            .eq(BlogPost::getSlug, normalizedSlug)
            .eq(BlogPost::getStatus, BlogPostStatus.PUBLISHED.name()));
        if (post == null) {
            throw new BusinessException(404, "博客文章不存在", 404);
        }
        String coverPublicUrl = null;
        if (post.getCoverAssetId() != null) {
            BlogAsset cover = blogAssetMapper.selectById(post.getCoverAssetId());
            if (cover != null) {
                coverPublicUrl = cover.getPublicUrl();
            }
        }
        return new BlogPublicDetailSnapshot(
            post.getId(),
            post.getTitle(),
            post.getSlug(),
            post.getSummary(),
            post.getContentObjectKey(),
            post.getCoverAssetId(),
            coverPublicUrl,
            post.getStatus(),
            post.getPublishedAt(),
            buildEtag(post, coverPublicUrl));
    }

    @Override
    public BlogPostPublicDetailDTO publicDetail(BlogPublicDetailSnapshot snapshot) {
        BlogPostPublicDetailDTO dto = new BlogPostPublicDetailDTO();
        dto.setId(snapshot.id());
        dto.setTitle(snapshot.title());
        dto.setSlug(snapshot.slug());
        dto.setSummary(snapshot.summary());
        dto.setCoverAssetId(snapshot.coverAssetId());
        dto.setCoverPublicUrl(snapshot.coverPublicUrl());
        dto.setPublishedAt(snapshot.publishedAt());
        dto.setContentMarkdown(contentStorage.readMarkdown(snapshot.contentObjectKey()));
        return dto;
    }

    private String buildEtag(BlogPost post, String coverPublicUrl) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("format", "blog-public-detail-1");
            canonical.put("id", post.getId());
            canonical.put("title", post.getTitle());
            canonical.put("slug", post.getSlug());
            canonical.put("summary", post.getSummary());
            canonical.put("contentObjectKey", post.getContentObjectKey());
            canonical.put("coverAssetId", post.getCoverAssetId());
            canonical.put("coverPublicUrl", coverPublicUrl);
            canonical.put("status", post.getStatus());
            canonical.put("publishedAt", post.getPublishedAt());
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(canonical));
            return "W/\"" + java.util.HexFormat.of().formatHex(digest) + "\"";
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot build blog ETag", ex);
        }
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

    private Set<String> knownPublicUrls(Long postId) {
        Set<String> urls = new HashSet<>();
        String publicBucket = ossService.getBucketName(OssSavePlaceEnum.PUBLIC);
        blogAssetMapper.selectList(new LambdaQueryWrapper<BlogAsset>()
                .eq(BlogAsset::getPostId, postId))
            .forEach(asset -> {
                if (StringUtils.hasText(asset.getPublicUrl())) {
                    urls.add(asset.getPublicUrl());
                }
                if (StringUtils.hasText(asset.getObjectKey())) {
                    if (StringUtils.hasText(publicBucket)) {
                        urls.add("/" + publicBucket + "/" + asset.getObjectKey());
                    }
                }
            });
        return urls;
    }

    private void insertAutoImageAssets(
            Long operatorId, Long postId, List<BlogContentStorageService.StoredMarkdownImage> images) {
        for (BlogContentStorageService.StoredMarkdownImage image : images) {
            BlogAsset asset = new BlogAsset();
            asset.setPostId(postId);
            asset.setAssetType(BlogAssetType.CONTENT_IMAGE.name());
            asset.setOriginalFilename(image.originalFilename());
            asset.setContentType(image.contentType());
            asset.setFileSize(image.fileSize());
            asset.setObjectKey(image.objectKey());
            asset.setPublicUrl(image.publicUrl());
            asset.setCreatedBy(operatorId);
            asset.setIsDeleted(false);
            blogAssetMapper.insert(asset);
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
            try {
                dto.setContentMarkdown(contentStorage.readMarkdown(post.getContentObjectKey()));
            } catch (Exception e) {
                log.warn("Failed to read markdown content for post {}: {}", post.getId(), e.getMessage());
            }
        }
        return dto;
    }

    private BlogPostPublicListDTO toPublicListDTO(BlogPost post, Map<Long, String> coverUrlMap) {
        BlogPostPublicListDTO dto = new BlogPostPublicListDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(post.getSummary());
        dto.setCoverAssetId(post.getCoverAssetId());
        dto.setCoverPublicUrl(post.getCoverAssetId() != null ? coverUrlMap.get(post.getCoverAssetId()) : null);
        dto.setPublishedAt(post.getPublishedAt());
        return dto;
    }

    private Map<Long, String> batchLoadCoverUrls(List<BlogPost> posts) {
        List<Long> ids = posts.stream()
            .map(BlogPost::getCoverAssetId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return blogAssetMapper.selectBatchIds(ids).stream()
            .filter(a -> StringUtils.hasText(a.getPublicUrl()))
            .collect(Collectors.toMap(BlogAsset::getId, BlogAsset::getPublicUrl));
    }

    private String validateSlug(String slug) {
        String normalized = requiredText(slug, "slug不能为空");
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw badRequest("slug格式不合法");
        }
        return normalized;
    }

    private String generateSlug() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
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
