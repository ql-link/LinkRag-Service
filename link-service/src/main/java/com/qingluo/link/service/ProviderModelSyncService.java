package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.ProviderModelSyncCandidate;
import com.qingluo.link.model.dto.entity.ProviderModelSyncJob;
import com.qingluo.link.model.dto.request.PublishModelSyncCandidateRequest;
import com.qingluo.link.model.dto.response.PageResult;

/**
 * 外部模型目录同步服务。
 */
public interface ProviderModelSyncService {

    /**
     * 手动刷新某厂商的外部模型目录，写入候选表，不直接影响正式目录。
     */
    ProviderModelSyncJob refreshProviderModels(Long providerId, String syncSource);

    /**
     * 分页查询同步任务。
     */
    PageResult<ProviderModelSyncJob> listJobs(int page, int size, Long providerId, String syncSource, String status);

    /**
     * 分页查询外部模型候选。
     */
    PageResult<ProviderModelSyncCandidate> listCandidates(int page, int size, Long providerId, Long jobId,
                                                          String reviewStatus, String capability);

    /**
     * 将候选发布到正式模型目录。
     */
    ProviderModel publishCandidate(Long candidateId, PublishModelSyncCandidateRequest request);

    /**
     * 更新候选审核状态。
     */
    ProviderModelSyncCandidate updateReviewStatus(Long candidateId, String reviewStatus);
}
