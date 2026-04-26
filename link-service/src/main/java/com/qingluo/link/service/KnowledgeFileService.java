package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库原文件服务。
 *
 * <p>一期服务边界只覆盖原文件上传事实：
 * 校验数据集归属、写入原文件表、上传私有 MinIO、失败重试、超时补偿和删除。
 *
 * <p>解析任务、解析进度、解析产物不放在本接口里扩展；
 * 二期会通过独立解析任务表和解析产物表承载，避免原文件服务承担过多职责。
 */
public interface KnowledgeFileService {

    /**
     * 上传原文件并记录上传状态。
     *
     * @param parseImmediately 一期仅接收兼容参数，不触发解析；二期由该开关控制是否自动投递解析任务
     */
    KnowledgeFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately);

    /**
     * 查询数据集下原文件列表，uploadStatus 只过滤上传状态，不过滤解析状态。
     */
    PageResult<KnowledgeFileDTO> list(Long userId, Long datasetId, String uploadStatus, int page, int pageSize);

    /**
     * 查询当前用户拥有的原文件详情。
     */
    KnowledgeFileDTO detail(Long userId, Long fileId);

    /**
     * 删除原文件数据库记录和对应 MinIO 私有对象。
     */
    void delete(Long userId, Long fileId);

    /**
     * 打开原文件本地可读资源，供内部下载接口返回文件流。
     */
    KnowledgeFileDownloadResource openOriginalFile(Long fileId);

    /**
     * 将长时间停留在 uploading 的记录标记为 failed，供定时任务和测试复用。
     */
    int markTimeoutUploadsFailed();
}
