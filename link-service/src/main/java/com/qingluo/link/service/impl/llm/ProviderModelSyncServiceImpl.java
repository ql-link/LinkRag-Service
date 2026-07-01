package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.ProviderModelSyncCandidateMapper;
import com.qingluo.link.mapper.ProviderModelSyncJobMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.ProviderModelSyncCandidate;
import com.qingluo.link.model.dto.entity.ProviderModelSyncJob;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.PublishModelSyncCandidateRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.ProviderModelSyncService;
import com.qingluo.link.service.llm.catalog.ExternalModelCatalogClient;
import com.qingluo.link.service.llm.catalog.ExternalModelCatalogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部模型目录同步服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProviderModelSyncServiceImpl implements ProviderModelSyncService {

    private static final String DEFAULT_SOURCE = "MODELS_DEV";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REVIEW_PENDING = "PENDING";
    private static final String REVIEW_PUBLISHED = "PUBLISHED";
    private static final String REVIEW_REJECTED = "REJECTED";

    private final ProviderModelSyncJobMapper syncJobMapper;
    private final ProviderModelSyncCandidateMapper syncCandidateMapper;
    private final ProviderModelMapper providerModelMapper;
    private final SystemProviderMapper systemProviderMapper;
    private final LLMCapabilityService llmCapabilityService;
    private final ProviderModelService providerModelService;
    private final List<ExternalModelCatalogClient> catalogClients;

    @Override
    public ProviderModelSyncJob refreshProviderModels(Long providerId, String syncSource) {
        SystemProvider provider = requireProvider(providerId);
        String source = normalizeSource(syncSource);
        ExternalModelCatalogClient client = requireClient(source);
        ProviderModelSyncJob job = createRunningJob(providerId, source);
        try {
            List<ExternalModelCatalogEntry> externalModels = client.listModels(provider);
            List<ProviderModel> localModels = providerModelMapper.selectList(
                    new LambdaQueryWrapper<ProviderModel>().eq(ProviderModel::getProviderId, providerId));
            RefreshStats stats = writeCandidates(job, provider, externalModels, localModels);
            job.setStatus(STATUS_SUCCESS);
            job.setAddedCount(stats.addedCount());
            job.setUpdatedCount(stats.updatedCount());
            job.setStaleCount(stats.staleCount());
            job.setFinishedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);
            return job;
        } catch (RuntimeException ex) {
            job.setStatus(STATUS_FAILED);
            job.setErrorMessage(limitMessage(ex.getMessage()));
            job.setFinishedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);
            throw ex;
        }
    }

    @Override
    public PageResult<ProviderModelSyncJob> listJobs(int page, int size, Long providerId, String syncSource,
                                                     String status) {
        LambdaQueryWrapper<ProviderModelSyncJob> wrapper = new LambdaQueryWrapper<ProviderModelSyncJob>()
                .orderByDesc(ProviderModelSyncJob::getStartedAt)
                .orderByDesc(ProviderModelSyncJob::getId);
        if (providerId != null) {
            wrapper.eq(ProviderModelSyncJob::getProviderId, providerId);
        }
        if (StringUtils.hasText(syncSource)) {
            wrapper.eq(ProviderModelSyncJob::getSyncSource, normalizeSource(syncSource));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProviderModelSyncJob::getStatus, status.toUpperCase(Locale.ROOT));
        }
        Page<ProviderModelSyncJob> pageParam = new Page<>(page, size);
        Page<ProviderModelSyncJob> result = syncJobMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public PageResult<ProviderModelSyncCandidate> listCandidates(int page, int size, Long providerId, Long jobId,
                                                                 String reviewStatus, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        LambdaQueryWrapper<ProviderModelSyncCandidate> wrapper =
                new LambdaQueryWrapper<ProviderModelSyncCandidate>()
                        .orderByDesc(ProviderModelSyncCandidate::getLastSeenAt)
                        .orderByDesc(ProviderModelSyncCandidate::getId);
        if (providerId != null) {
            wrapper.eq(ProviderModelSyncCandidate::getProviderId, providerId);
        }
        if (jobId != null) {
            wrapper.eq(ProviderModelSyncCandidate::getJobId, jobId);
        }
        if (StringUtils.hasText(reviewStatus)) {
            wrapper.eq(ProviderModelSyncCandidate::getReviewStatus,
                    reviewStatus.toUpperCase(Locale.ROOT));
        }
        if (normalizedCapability != null) {
            wrapper.eq(ProviderModelSyncCandidate::getInferredCapability, normalizedCapability);
        }
        Page<ProviderModelSyncCandidate> pageParam = new Page<>(page, size);
        Page<ProviderModelSyncCandidate> result = syncCandidateMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public ProviderModel publishCandidate(Long candidateId, PublishModelSyncCandidateRequest request) {
        ProviderModelSyncCandidate candidate = requireCandidate(candidateId);
        String modelName = firstText(request.getModelName(), candidate.getModelName());
        String displayName = request.getDisplayName() != null ? request.getDisplayName() : candidate.getDisplayName();
        String capability = firstText(request.getCapability(), candidate.getInferredCapability());
        String protocol = firstText(request.getProtocol(), candidate.getInferredProtocol());
        String apiBaseUrl = firstText(request.getApiBaseUrl(), candidate.getInferredApiBaseUrl());

        ProviderModel model = providerModelService.addModelCapability(candidate.getProviderId(), modelName,
                displayName, capability, protocol, apiBaseUrl);
        candidate.setReviewStatus(REVIEW_PUBLISHED);
        candidate.setMatchedProviderModelId(model.getId());
        syncCandidateMapper.updateById(candidate);
        return model;
    }

    @Override
    @Transactional
    public ProviderModelSyncCandidate updateReviewStatus(Long candidateId, String reviewStatus) {
        ProviderModelSyncCandidate candidate = requireCandidate(candidateId);
        String normalized = reviewStatus.toUpperCase(Locale.ROOT);
        if (!REVIEW_PENDING.equals(normalized) && !REVIEW_REJECTED.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY,
                    "候选审核状态只支持 PENDING/REJECTED");
        }
        candidate.setReviewStatus(normalized);
        syncCandidateMapper.updateById(candidate);
        return candidate;
    }

    private ProviderModelSyncJob createRunningJob(Long providerId, String source) {
        ProviderModelSyncJob job = new ProviderModelSyncJob();
        job.setProviderId(providerId);
        job.setSyncSource(source);
        job.setStatus(STATUS_RUNNING);
        job.setAddedCount(0);
        job.setUpdatedCount(0);
        job.setStaleCount(0);
        job.setStartedAt(LocalDateTime.now());
        syncJobMapper.insert(job);
        return job;
    }

    private RefreshStats writeCandidates(ProviderModelSyncJob job, SystemProvider provider,
                                         List<ExternalModelCatalogEntry> externalModels,
                                         List<ProviderModel> localModels) {
        Map<ModelCapKey, ProviderModel> localByKey = localModels.stream()
                .collect(Collectors.toMap(
                        model -> new ModelCapKey(model.getModelName(), model.getCapability()),
                        Function.identity(),
                        (left, right) -> left));
        Set<ModelCapKey> externalKeys = new LinkedHashSet<>();
        int added = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ExternalModelCatalogEntry external : externalModels) {
            for (String capability : external.getCapabilities()) {
                String normalizedCapability = capability.toUpperCase(Locale.ROOT);
                ModelCapKey key = new ModelCapKey(external.getModelName(), normalizedCapability);
                externalKeys.add(key);
                ProviderModel matched = localByKey.get(key);
                ProviderModelSyncCandidate candidate = toCandidate(job, provider, external, normalizedCapability,
                        matched, now);
                syncCandidateMapper.insert(candidate);
                if (matched == null) {
                    added++;
                } else {
                    updated++;
                }
            }
        }
        long stale = localByKey.keySet().stream().filter(key -> !externalKeys.contains(key)).count();
        return new RefreshStats(added, updated, Math.toIntExact(stale));
    }

    private ProviderModelSyncCandidate toCandidate(ProviderModelSyncJob job, SystemProvider provider,
                                                   ExternalModelCatalogEntry external, String capability,
                                                   ProviderModel matched, LocalDateTime now) {
        ProviderModelSyncCandidate candidate = new ProviderModelSyncCandidate();
        candidate.setJobId(job.getId());
        candidate.setProviderId(provider.getId());
        candidate.setSyncSource(job.getSyncSource());
        candidate.setExternalModelId(external.getExternalModelId());
        candidate.setModelName(external.getModelName());
        candidate.setDisplayName(external.getDisplayName());
        candidate.setInferredCapability(capability);
        candidate.setInferredProtocol(inferProtocol(provider.getProviderType(), capability));
        candidate.setInferredApiBaseUrl(inferApiBaseUrl(provider, candidate.getInferredProtocol(), capability));
        candidate.setContextWindow(external.getContextWindow());
        candidate.setMaxOutputTokens(external.getMaxOutputTokens());
        candidate.setReleaseDate(external.getReleaseDate());
        candidate.setInputModalities(external.getInputModalitiesJson());
        candidate.setOutputModalities(external.getOutputModalitiesJson());
        candidate.setRawMetadata(external.getRawMetadataJson());
        candidate.setReviewStatus(REVIEW_PENDING);
        candidate.setMatchedProviderModelId(matched == null ? null : matched.getId());
        candidate.setLastSeenAt(now);
        return candidate;
    }

    private String inferProtocol(String providerType, String capability) {
        if ("claude".equals(providerType)) {
            return "anthropic";
        }
        if ("gemini".equals(providerType)) {
            return "google";
        }
        if ("jina".equals(providerType)) {
            return "jina";
        }
        if ("aliyun".equals(providerType) && ("RERANK".equals(capability) || "ASR".equals(capability))) {
            return "dashscope";
        }
        return "openai";
    }

    private String inferApiBaseUrl(SystemProvider provider, String protocol, String capability) {
        String base = provider.getApiBaseUrl();
        if (!StringUtils.hasText(base)) {
            return null;
        }
        return switch (protocol) {
            case "anthropic" -> appendAnthropicMessages(base);
            case "google" -> base;
            case "jina" -> appendSuffix(base, "RERANK".equals(capability) ? "/rerank" : "/embeddings");
            case "dashscope" -> "RERANK".equals(capability)
                    ? appendSuffix(base, "/services/rerank/text-rerank/text-rerank")
                    : base;
            default -> appendOpenAiSuffix(base, capability);
        };
    }

    private String appendOpenAiSuffix(String base, String capability) {
        return switch (capability) {
            case "CHAT", "VISION" -> appendSuffix(base, "/chat/completions");
            case "EMBEDDING", "SPARSE_EMBEDDING" -> appendSuffix(base, "/embeddings");
            case "ASR" -> appendSuffix(base, "/audio/transcriptions");
            default -> base;
        };
    }

    private String appendAnthropicMessages(String base) {
        String normalized = trimTrailingSlash(base);
        if (normalized.endsWith("/v1")) {
            return normalized + "/messages";
        }
        return normalized + "/v1/messages";
    }

    private String appendSuffix(String base, String suffix) {
        String normalized = trimTrailingSlash(base);
        if (normalized.endsWith(suffix)) {
            return normalized;
        }
        return normalized + suffix;
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private SystemProvider requireProvider(Long providerId) {
        SystemProvider provider = systemProviderMapper.selectById(providerId);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }

    private ProviderModelSyncCandidate requireCandidate(Long candidateId) {
        ProviderModelSyncCandidate candidate = syncCandidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new NotFoundException(ErrorCode.MODEL_SYNC_CANDIDATE_NOT_FOUND);
        }
        return candidate;
    }

    private ExternalModelCatalogClient requireClient(String source) {
        Map<String, ExternalModelCatalogClient> clientsBySource = new HashMap<>();
        for (ExternalModelCatalogClient client : catalogClients) {
            clientsBySource.put(client.source(), client);
        }
        ExternalModelCatalogClient client = clientsBySource.get(source);
        if (client == null) {
            throw new BusinessException(ErrorCode.MODEL_SYNC_SOURCE_UNSUPPORTED);
        }
        return client;
    }

    private String normalizeSource(String source) {
        return StringUtils.hasText(source) ? source.toUpperCase(Locale.ROOT) : DEFAULT_SOURCE;
    }

    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String limitMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private record RefreshStats(int addedCount, int updatedCount, int staleCount) {
    }

    private record ModelCapKey(String modelName, String capability) {

        private ModelCapKey {
            Objects.requireNonNull(modelName);
            Objects.requireNonNull(capability);
        }
    }
}
