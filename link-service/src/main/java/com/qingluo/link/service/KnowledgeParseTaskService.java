package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import java.util.List;

/**
 * 文件解析任务服务。
 *
 * <p>Java 只创建解析任务、投递 MQ、做投递补偿和面向前端查询；
 * Python 负责真正解析并更新任务状态与最新解析产物。
 */
public interface KnowledgeParseTaskService {

    FileParseSubmitDTO submitManualParse(Long userId, Long fileId);

    void submitAutoParseAfterUpload(Long userId, KnowledgeOriginalFile originalFile);

    List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds);

    int compensateCreatedTasks();
}
