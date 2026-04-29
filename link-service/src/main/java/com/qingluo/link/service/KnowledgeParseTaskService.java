package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import java.util.List;

/**
 * 文件解析任务服务。
 *
 * <p>Java 只负责解析受理、事务内更新 latest_parse_task_id、同步发送 MQ 和结果查询。
 * Python 负责消费任务、创建解析日志、执行解析并更新最终状态。
 *
 * <p>二期核心变化：
 * <ul>
 *   <li>事务内先更新 latest_parse_task_id，再同步发送 MQ</li>
 *   <li>MQ 发送失败则事务回滚，用户看到"解析提交失败"</li>
 *   <li>MQ 消息体包含 parsed_file_id，Python 可直接更新解析聚合记录</li>
 *   <li>同一原文件存在进行中任务时，拒绝重复提交</li>
 * </ul>
 */
public interface KnowledgeParseTaskService {

    /**
     * 手动触发解析。
     *
     * <p>校验文件归属、上传成功、解析聚合记录存在、无进行中任务后，
     * 在事务内更新 latest_parse_task_id 并发送 MQ。
     */
    FileParseSubmitDTO submitManualParse(Long userId, Long fileId);

    /**
     * 上传成功后自动触发解析。
     *
     * <p>与手动解析流程一致，但自动模式下若存在进行中任务则跳过而非拒绝。
     */
    void submitAutoParseAfterUpload(Long userId, KnowledgeOriginalFile originalFile);

    /**
     * 查询文件列表的解析结果。
     *
     * <p>按数据库终态返回，前端在断线/刷新/超时未收到终态时兜底查询。
     */
    List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds);
}
