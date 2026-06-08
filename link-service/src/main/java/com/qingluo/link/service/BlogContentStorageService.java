package com.qingluo.link.service;

import com.qingluo.link.model.enums.BlogAssetType;
import org.springframework.web.multipart.MultipartFile;

public interface BlogContentStorageService {

    String uploadMarkdown(Long postId, MultipartFile file);

    String readMarkdown(String objectKey);

    boolean existsMarkdown(String objectKey);

    StoredObject uploadImage(Long postId, BlogAssetType assetType, MultipartFile file);

    record StoredObject(String objectKey, String publicUrl) {
    }
}
