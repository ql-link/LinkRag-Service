package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import java.util.List;

/**
 * Java 侧解析任务受理与查询服务。
 */
public interface KnowledgeParseTaskService {

    FileParseSubmitDTO submitManualParse(Long userId, Long fileId);

    void submitAutoParseAfterUpload(Long userId, KnowledgeOriginalFile originalFile);

    List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds);
}
