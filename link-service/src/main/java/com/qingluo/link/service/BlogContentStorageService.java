package com.qingluo.link.service;

import com.qingluo.link.model.enums.BlogAssetType;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public interface BlogContentStorageService {

    ProcessedMarkdown importMarkdown(Long postId, MultipartFile file, Set<String> knownPublicUrls);

    ProcessedMarkdown saveMarkdown(Long postId, String markdown, Set<String> knownPublicUrls);

    String readMarkdown(String objectKey);

    boolean existsMarkdown(String objectKey);

    StoredObject uploadImage(Long postId, BlogAssetType assetType, MultipartFile file);

    record ProcessedMarkdown(String objectKey, String contentMarkdown, List<StoredMarkdownImage> images) {
    }

    record StoredMarkdownImage(
            String objectKey,
            String publicUrl,
            String originalFilename,
            String contentType,
            long fileSize) {
    }

    record StoredObject(String objectKey, String publicUrl) {
    }
}
