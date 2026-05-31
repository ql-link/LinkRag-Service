package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;

public interface AdminDocumentFileConfigService {

    DocumentFileConfigDTO getCurrentConfig();

    void updateConfig(Long adminUserId, UpdateDocumentFileConfigRequest request);
}
