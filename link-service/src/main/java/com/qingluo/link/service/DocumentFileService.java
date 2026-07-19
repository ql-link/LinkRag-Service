package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.model.dto.response.DocumentFileCapabilitiesDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.document.DocumentFileUploadCommand;
import org.springframework.web.multipart.MultipartFile;
import com.qingluo.link.service.DocumentFileDownloadResource;

public interface DocumentFileService {

    DocumentFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately);

    DocumentFileDTO upload(Long userId, Long datasetId, DocumentFileUploadCommand command);

    DocumentFileCapabilitiesDTO getCapabilities();

    PageResult<DocumentFileDTO> list(Long userId, Long datasetId, String uploadStatus, int page, int pageSize);

    PageResult<DocumentFileDTO> listRecent(Long userId, int page, int pageSize);

    DocumentFileDTO detail(Long userId, Long fileId);

    void delete(Long userId, Long fileId);

    DocumentFileDownloadResource openOriginalFile(Long fileId);
}
