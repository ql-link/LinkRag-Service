package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.BlogAssetDTO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface BlogAssetService {

    List<BlogAssetDTO> list(Long postId, String assetType);

    BlogAssetDTO upload(Long operatorId, Long postId, String assetType, MultipartFile file);

    void delete(Long operatorId, Long postId, Long assetId);
}
