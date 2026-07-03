package com.qingluo.link.service.impl.blog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.log.AuditLog;
import com.qingluo.link.mapper.BlogAssetMapper;
import com.qingluo.link.mapper.BlogPostMapper;
import com.qingluo.link.model.dto.entity.BlogAsset;
import com.qingluo.link.model.dto.entity.BlogPost;
import com.qingluo.link.model.dto.response.BlogAssetDTO;
import com.qingluo.link.model.enums.BlogAssetType;
import com.qingluo.link.service.BlogAssetService;
import com.qingluo.link.service.BlogContentStorageService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogAssetServiceImpl implements BlogAssetService {

    private static final Logger log = LoggerFactory.getLogger(BlogAssetServiceImpl.class);

    private final BlogPostMapper blogPostMapper;
    private final BlogAssetMapper blogAssetMapper;
    private final BlogContentStorageService contentStorage;
    private final IOssService ossService;

    @Override
    public List<BlogAssetDTO> list(Long postId, String assetType) {
        getActivePost(postId);
        LambdaQueryWrapper<BlogAsset> query = new LambdaQueryWrapper<BlogAsset>()
            .eq(BlogAsset::getPostId, postId)
            .orderByDesc(BlogAsset::getCreatedAt)
            .orderByDesc(BlogAsset::getId);
        if (StringUtils.hasText(assetType)) {
            query.eq(BlogAsset::getAssetType, parseAssetType(assetType).name());
        }
        return blogAssetMapper.selectList(query)
            .stream()
            .map(this::toDTO)
            .toList();
    }

    @Override
    @Transactional
    public BlogAssetDTO upload(Long operatorId, Long postId, String assetType, MultipartFile file) {
        BlogPost post = getActivePost(postId);
        BlogAssetType type = parseAssetType(assetType);
        BlogContentStorageService.StoredObject stored = contentStorage.uploadImage(postId, type, file);

        BlogAsset asset = new BlogAsset();
        asset.setPostId(postId);
        asset.setAssetType(type.name());
        asset.setOriginalFilename(file.getOriginalFilename());
        asset.setContentType(file.getContentType());
        asset.setFileSize(file.getSize());
        asset.setObjectKey(stored.objectKey());
        asset.setPublicUrl(stored.publicUrl());
        asset.setCreatedBy(operatorId);
        asset.setIsDeleted(false);
        blogAssetMapper.insert(asset);

        if (type == BlogAssetType.COVER) {
            Long oldCoverAssetId = post.getCoverAssetId();
            BlogPost update = new BlogPost();
            update.setId(postId);
            update.setCoverAssetId(asset.getId());
            blogPostMapper.updateById(update);
            deleteOldCover(oldCoverAssetId, postId);
        }

        AuditLog.event("BLOG_ASSET_UPLOAD", "operatorId={}, postId={}, assetId={}, assetType={}",
            operatorId, postId, asset.getId(), type.name());
        return toDTO(asset);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long postId, Long assetId) {
        BlogPost post = getActivePost(postId);
        BlogAsset asset = blogAssetMapper.selectOne(new LambdaQueryWrapper<BlogAsset>()
            .eq(BlogAsset::getId, assetId)
            .eq(BlogAsset::getPostId, postId));
        if (asset == null) {
            throw new BusinessException(404, "博客资源不存在", 404);
        }

        if (BlogAssetType.CONTENT_IMAGE.name().equals(asset.getAssetType())
                && StringUtils.hasText(post.getContentObjectKey())) {
            String markdown = contentStorage.readMarkdown(post.getContentObjectKey());
            if (containsAnyKnownReference(markdown, asset)) {
                throw badRequest("该图片仍被正文引用，删除前请先从正文中移除");
            }
        }

        blogAssetMapper.deleteById(assetId);
        if (assetId.equals(post.getCoverAssetId())) {
            blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, postId)
                .set(BlogPost::getCoverAssetId, null));
        }
        if (!ossService.deleteFile(OssSavePlaceEnum.PUBLIC, asset.getObjectKey())) {
            throw new BusinessException(50002, "博客资源对象删除失败", 500);
        }

        AuditLog.event("BLOG_ASSET_DELETE", "operatorId={}, postId={}, assetId={}",
            operatorId, postId, assetId);
    }

    private BlogPost getActivePost(Long postId) {
        BlogPost post = blogPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "博客文章不存在", 404);
        }
        return post;
    }

    private BlogAssetType parseAssetType(String assetType) {
        if (assetType == null) {
            throw badRequest("资源类型不能为空");
        }
        try {
            return BlogAssetType.valueOf(assetType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw badRequest("资源类型不支持");
        }
    }

    private BlogAssetDTO toDTO(BlogAsset asset) {
        BlogAssetDTO dto = new BlogAssetDTO();
        dto.setId(asset.getId());
        dto.setPostId(asset.getPostId());
        dto.setAssetType(asset.getAssetType());
        dto.setOriginalFilename(asset.getOriginalFilename());
        dto.setContentType(asset.getContentType());
        dto.setFileSize(asset.getFileSize());
        dto.setObjectKey(asset.getObjectKey());
        dto.setPublicUrl(asset.getPublicUrl());
        if (BlogAssetType.CONTENT_IMAGE.name().equals(asset.getAssetType())) {
            dto.setMarkdownText("![" + markdownAlt(asset.getOriginalFilename()) + "](" + asset.getPublicUrl() + ")");
        }
        dto.setCreatedBy(asset.getCreatedBy());
        dto.setCreatedAt(asset.getCreatedAt());
        dto.setUpdatedAt(asset.getUpdatedAt());
        return dto;
    }

    private void deleteOldCover(Long oldCoverAssetId, Long postId) {
        if (oldCoverAssetId == null) {
            return;
        }
        BlogAsset old = blogAssetMapper.selectById(oldCoverAssetId);
        if (old == null) {
            return;
        }
        blogAssetMapper.deleteById(oldCoverAssetId);
        if (!ossService.deleteFile(OssSavePlaceEnum.PUBLIC, old.getObjectKey())) {
            log.warn("Failed to delete old cover object, postId={}, assetId={}, key={}",
                postId, oldCoverAssetId, old.getObjectKey());
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(40001, message, 400);
    }

    private boolean containsAnyKnownReference(String markdown, BlogAsset asset) {
        if (!StringUtils.hasText(markdown)) {
            return false;
        }
        for (String reference : knownPublicReferences(asset)) {
            if (markdown.contains(reference)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> knownPublicReferences(BlogAsset asset) {
        Set<String> references = new HashSet<>();
        if (StringUtils.hasText(asset.getPublicUrl())) {
            references.add(asset.getPublicUrl());
        }
        if (StringUtils.hasText(asset.getObjectKey())) {
            String publicBucket = ossService.getBucketName(OssSavePlaceEnum.PUBLIC);
            if (StringUtils.hasText(publicBucket)) {
                references.add("/" + publicBucket + "/" + asset.getObjectKey());
            }
        }
        return references;
    }

    private String markdownAlt(String filename) {
        return StringUtils.hasText(filename) ? filename : "image";
    }
}
