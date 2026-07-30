package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.model.dto.cache.DatasetParseConfigSnapshot;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
import com.qingluo.link.service.DatasetModelBindingValidator;
import com.qingluo.link.service.DatasetParseConfigService;
import com.qingluo.link.service.DatasetService;
import com.qingluo.link.service.cache.DatasetParseConfigCache;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据集解析/检索配置管理实现：Java 管理配置行，并为前端回显补齐召回新增项默认值。
 */
@Service
@RequiredArgsConstructor
public class DatasetParseConfigServiceImpl implements DatasetParseConfigService {

    private static final List<String> DEFAULT_RECALL_ENABLED_SOURCES = List.of("bm25", "sparse", "dense");
    private static final int DEFAULT_RERANK_TOP_N = 8;
    private static final boolean DEFAULT_RECALL_STRICT = false;
    private static final Set<String> ALLOWED_STAGE_TWO_ALGORITHMS = Set.of("noop", "semantic_depth_window");
    private static final Set<String> ALLOWED_RECALL_SOURCES = Set.of("bm25", "sparse", "dense");

    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final DatasetService datasetService;
    private final DatasetModelBindingValidator modelBindingValidator;
    private final DatasetParseConfigCache datasetParseConfigCache;

    @Override
    public DatasetParseConfigResponse getConfig(Long userId, Long datasetId) {
        // 归属校验：越权/不存在抛 BusinessException(404)，复用数据集服务避免重复查询。
        datasetService.detail(userId, datasetId);
        DatasetParseConfigSnapshot snapshot = datasetParseConfigCache.get(userId, datasetId, () -> {
            DatasetParseConfig entity = selectByOwner(userId, datasetId);
            return entity != null ? snapshotOf(entity) : null;
        });
        return snapshot != null ? assembleResponse(snapshot) : emptyResponse();
    }

    @Override
    @Transactional
    public DatasetParseConfigResponse updateConfig(Long userId, Long datasetId,
                                                   UpdateDatasetParseConfigRequest request) {
        datasetService.detail(userId, datasetId);
        DatasetParseConfig existing = selectByOwner(userId, datasetId);
        if (existing != null) {
            DatasetParseConfigResponse response = overwriteRow(userId, existing, request);
            datasetParseConfigCache.evict(datasetId);
            return response;
        }

        DatasetParseConfig created = new DatasetParseConfig();
        created.setUserId(userId);
        created.setDatasetId(datasetId);
        created.setIsActive(true);
        applyModelBindings(userId, created, null, request);
        applyConfigs(created, request);
        try {
            datasetParseConfigMapper.insert(created);
            DatasetParseConfigResponse response = assembleResponse(created);
            datasetParseConfigCache.evict(datasetId);
            return response;
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一键 uk_user_dataset 撞行：转为更新已存在的行。
            DatasetParseConfig concurrent = selectByOwner(userId, datasetId);
            DatasetParseConfigResponse response = overwriteRow(userId, concurrent, request);
            datasetParseConfigCache.evict(datasetId);
            return response;
        }
    }

    /**
     * 整行覆盖四类配置。仅 set 主键与四类列，刻意不带 created_at/updated_at——
     * 若把 select 出的旧 updated_at 一并 updateById，会以旧值显式赋值，抑制 DB 的
     * ON UPDATE CURRENT_TIMESTAMP 自动刷新，导致「最后更新时间」不变。
     */
    private DatasetParseConfigResponse overwriteRow(Long userId, DatasetParseConfig existing,
                                                    UpdateDatasetParseConfigRequest request) {
        DatasetParseConfig update = new DatasetParseConfig();
        update.setId(existing.getId());
        applyModelBindings(userId, update, existing, request);
        applyConfigs(update, request);
        datasetParseConfigMapper.updateById(update);
        return assembleResponse(update);
    }

    private DatasetParseConfig selectByOwner(Long userId, Long datasetId) {
        return datasetParseConfigMapper.selectOne(new LambdaQueryWrapper<DatasetParseConfig>()
            .eq(DatasetParseConfig::getUserId, userId)
            .eq(DatasetParseConfig::getDatasetId, datasetId));
    }

    /**
     * PUT 全量：四类列整体按请求重写，请求未提供的类写空对象（序列化为 {@code {}}），不补字段默认。
     */
    private void applyConfigs(DatasetParseConfig entity, UpdateDatasetParseConfigRequest req) {
        entity.setChunkingConfig(normalizeChunking(req.getChunking()));
        entity.setEnhancementConfig(req.getEnhancement() != null ? req.getEnhancement() : new EnhancementConfig());
        entity.setPdfConfig(req.getPdf() != null ? req.getPdf() : new PdfConfig());
        entity.setRecallConfig(normalizeForStorage(req.getRecall()));
    }

    private DatasetParseConfigResponse assembleResponse(DatasetParseConfig entity) {
        return assembleResponse(snapshotOf(entity));
    }

    private DatasetParseConfigResponse assembleResponse(DatasetParseConfigSnapshot snapshot) {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(snapshot.getChunkingConfig() != null
            ? snapshot.getChunkingConfig() : new ChunkingConfig());
        resp.setSparseEmbeddingConfigId(snapshot.getSparseEmbeddingConfigId());
        resp.setDenseEmbeddingConfigId(snapshot.getDenseEmbeddingConfigId());
        resp.setEnhancementChatConfigId(snapshot.getEnhancementChatConfigId());
        resp.setEnhancementVisionConfigId(snapshot.getEnhancementVisionConfigId());
        resp.setRerankConfigId(snapshot.getRerankConfigId());
        resp.setEnhancement(snapshot.getEnhancementConfig() != null
            ? snapshot.getEnhancementConfig() : new EnhancementConfig());
        resp.setPdf(snapshot.getPdfConfig() != null ? snapshot.getPdfConfig() : new PdfConfig());
        resp.setRecall(fillRecallDefaults(snapshot.getRecallConfig()));
        return resp;
    }

    private DatasetParseConfigSnapshot snapshotOf(DatasetParseConfig entity) {
        return new DatasetParseConfigSnapshot(
            entity.getUserId(),
            entity.getDatasetId(),
            entity.getSparseEmbeddingConfigId(),
            entity.getDenseEmbeddingConfigId(),
            entity.getEnhancementChatConfigId(),
            entity.getEnhancementVisionConfigId(),
            entity.getRerankConfigId(),
            entity.getChunkingConfig(),
            entity.getEnhancementConfig(),
            entity.getPdfConfig(),
            entity.getRecallConfig(),
            entity.getIsActive());
    }

    private DatasetParseConfigResponse emptyResponse() {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(new ChunkingConfig());
        resp.setSparseEmbeddingConfigId(null);
        resp.setDenseEmbeddingConfigId(null);
        resp.setEnhancementChatConfigId(null);
        resp.setEnhancementVisionConfigId(null);
        resp.setRerankConfigId(null);
        resp.setEnhancement(new EnhancementConfig());
        resp.setPdf(new PdfConfig());
        resp.setRecall(fillRecallDefaults(new RecallConfig()));
        return resp;
    }

    private RecallConfig normalizeForStorage(RecallConfig source) {
        if (source == null) {
            return new RecallConfig();
        }
        RecallConfig normalized = copyRecall(source);
        normalized.setRecallEnabledSources(normalizeRecallSources(source.getRecallEnabledSources()));
        validatePositive("recall_result_limit", source.getRecallResultLimit());
        validatePositive("bm25_top_k", source.getBm25TopK());
        validatePositive("sparse_top_k", source.getSparseTopK());
        validatePositive("dense_top_k", source.getDenseTopK());
        validatePositive("rerank_top_n", source.getRerankTopN());
        validateNonNegativeFinite("sparse_score_threshold", source.getSparseScoreThreshold());
        validateNonNegativeFinite("dense_score_threshold", source.getDenseScoreThreshold());
        validateNonNegativeFinite("fusion_bm25_weight", source.getFusionBm25Weight());
        validateNonNegativeFinite("fusion_sparse_weight", source.getFusionSparseWeight());
        validateNonNegativeFinite("fusion_dense_weight", source.getFusionDenseWeight());
        return normalized;
    }

    private void applyModelBindings(Long userId, DatasetParseConfig target, DatasetParseConfig existing,
                                    UpdateDatasetParseConfigRequest request) {
        DatasetModelBindingValidator.ValidatedBindings bindings =
            modelBindingValidator.validateForUpdate(userId, existing, request);
        target.setDenseEmbeddingConfigId(bindings.denseConfigId());
        target.setSparseEmbeddingConfigId(bindings.sparseConfigId());
        target.setEnhancementChatConfigId(bindings.enhancementChatConfigId());
        target.setEnhancementVisionConfigId(bindings.enhancementVisionConfigId());
        target.setRerankConfigId(bindings.rerankConfigId());
    }

    private ChunkingConfig normalizeChunking(ChunkingConfig source) {
        if (source == null) {
            return new ChunkingConfig();
        }
        ChunkingConfig normalized = copyChunking(source);
        normalized.setStageTwoAlgorithm(normalizeEnum(
            source.getStageTwoAlgorithm(),
            ALLOWED_STAGE_TWO_ALGORITHMS,
            "stage_two_algorithm 仅支持 noop/semantic_depth_window"));
        validateChunkBounds(source);
        return normalized;
    }

    private void validateChunkBounds(ChunkingConfig chunking) {
        Integer min = chunking.getMinCandidateChunkTokens();
        Integer max = chunking.getMaxChunkTokens();
        Integer hardMax = chunking.getHardMaxTokens();
        if (min != null && max != null && max < min) {
            throw new BusinessException(400, "max_chunk_tokens 必须大于等于 min_candidate_chunk_tokens", 400);
        }
        if (max != null && hardMax != null && hardMax < max) {
            throw new BusinessException(400, "hard_max_tokens 必须大于等于 max_chunk_tokens", 400);
        }
    }

    private String normalizeEnum(String value, Set<String> allowed, String message) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(400, message, 400);
        }
        return normalized;
    }

    private List<String> normalizeRecallSources(List<String> sources) {
        if (sources == null) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            String trimmed = source.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String sourceName = trimmed.toLowerCase(Locale.ROOT);
            if (!ALLOWED_RECALL_SOURCES.contains(sourceName)) {
                throw new BusinessException(400, "recall_enabled_sources 仅支持 bm25/sparse/dense", 400);
            }
            normalized.add(sourceName);
        }
        return new ArrayList<>(normalized);
    }

    private void validatePositive(String fieldName, Integer value) {
        if (value != null && value <= 0) {
            throw new BusinessException(400, fieldName + " 必须为正整数", 400);
        }
    }

    private void validateNonNegativeFinite(String fieldName, Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new BusinessException(400, fieldName + " 必须是不小于 0 的有限数", 400);
        }
    }

    private RecallConfig fillRecallDefaults(RecallConfig source) {
        RecallConfig filled = source != null ? copyRecall(source) : new RecallConfig();
        if (filled.getEnableRerank() == null) {
            filled.setEnableRerank(false);
        }
        if (filled.getRecallEnabledSources() == null) {
            filled.setRecallEnabledSources(DEFAULT_RECALL_ENABLED_SOURCES);
        }
        if (filled.getRerankTopN() == null) {
            filled.setRerankTopN(DEFAULT_RERANK_TOP_N);
        }
        if (filled.getRecallStrict() == null) {
            filled.setRecallStrict(DEFAULT_RECALL_STRICT);
        }
        return filled;
    }

    private ChunkingConfig copyChunking(ChunkingConfig source) {
        ChunkingConfig copy = new ChunkingConfig();
        copy.setHeadingBreakLevel(source.getHeadingBreakLevel());
        copy.setMinCandidateChunkTokens(source.getMinCandidateChunkTokens());
        copy.setOverlapTokens(source.getOverlapTokens());
        copy.setMaxChunkTokens(source.getMaxChunkTokens());
        copy.setHardMaxTokens(source.getHardMaxTokens());
        copy.setStageTwoAlgorithm(source.getStageTwoAlgorithm());
        copy.setProtectedNeighborOverlap(source.getProtectedNeighborOverlap());
        return copy;
    }

    private RecallConfig copyRecall(RecallConfig source) {
        RecallConfig copy = new RecallConfig();
        copy.setEnableRerank(source.getEnableRerank());
        copy.setRecallResultLimit(source.getRecallResultLimit());
        copy.setRecallContextTokenBudget(source.getRecallContextTokenBudget());
        copy.setBm25TopK(source.getBm25TopK());
        copy.setSparseTopK(source.getSparseTopK());
        copy.setSparseScoreThreshold(source.getSparseScoreThreshold());
        copy.setDenseTopK(source.getDenseTopK());
        copy.setDenseScoreThreshold(source.getDenseScoreThreshold());
        copy.setRecallEnabledSources(source.getRecallEnabledSources());
        copy.setFusionBm25Weight(source.getFusionBm25Weight());
        copy.setFusionSparseWeight(source.getFusionSparseWeight());
        copy.setFusionDenseWeight(source.getFusionDenseWeight());
        copy.setRerankTopN(source.getRerankTopN());
        copy.setRecallStrict(source.getRecallStrict());
        return copy;
    }
}
