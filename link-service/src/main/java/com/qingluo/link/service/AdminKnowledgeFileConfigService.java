package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.UpdateKnowledgeFileConfigRequest;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;

public interface AdminKnowledgeFileConfigService {

    KnowledgeFileConfigDTO getCurrentConfig();

    void updateConfig(Long adminUserId, UpdateKnowledgeFileConfigRequest request);
}
