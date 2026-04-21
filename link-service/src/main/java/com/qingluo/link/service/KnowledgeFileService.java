package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeFileService {

    KnowledgeFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately);

    PageResult<KnowledgeFileDTO> list(Long userId, Long datasetId, String uploadStatus,
                                      String parseNoticeStatus, String parseStatus, int page, int pageSize);

    KnowledgeFileDTO detail(Long userId, Long fileId);

    KnowledgeFileDTO createParseTask(Long userId, Long fileId);

    void delete(Long userId, Long fileId);

    KnowledgeFileDownloadResource openOriginalFile(Long fileId, String taskId);
}
