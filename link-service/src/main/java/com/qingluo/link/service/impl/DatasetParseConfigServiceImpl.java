package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
import com.qingluo.link.service.DatasetEmbeddingConfigValidator;
import com.qingluo.link.service.DatasetParseConfigService;
import com.qingluo.link.service.DatasetService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final Set<String> ALLOWED_RECALL_SOURCES = Set.of("bm25", "sparse", "dense");

    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final DatasetService datasetService;
    private final DatasetEmbeddingConfigValidator embeddingConfigValidator;

    @Override
    public DatasetParseConfigResponse getConfig(Long userId, Long datasetId) {
        // 归属校验：越权/不存在抛 BusinessException(404)，复用数据集服务避免重复查询。
        datasetService.detail(userId, datasetId);
        DatasetParseConfig entity = selectByOwner(userId, datasetId);
        return entity != null ? assembleResponse(entity) : emptyResponse();
    }

    @Override
    @Transactional
    public DatasetParseConfigResponse updateConfig(Long userId, Long datasetId,
                                                   UpdateDatasetParseConfigRequest request) {
        datasetService.detail(userId, datasetId);
        DatasetParseConfig existing = selectByOwner(userId, datasetId);
        if (existing != null) {
            return overwriteRow(userId, existing, request);
        }

        DatasetParseConfig created = new DatasetParseConfig();
        created.setUserId(userId);
        created.setDatasetId(datasetId);
        created.setIsActive(true);
        applyModelBindings(userId, created, null, request);
        applyConfigs(created, request);
        try {
            datasetParseConfigMapper.insert(created);
            return assembleResponse(created);
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一键 uk_user_dataset 撞行：转为更新已存在的行。
            DatasetParseConfig concurrent = selectByOwner(userId, datasetId);
            return overwriteRow(userId, concurrent, request);
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
        entity.setChunkingConfig(req.getChunking() != null ? req.getChunking() : new ChunkingConfig());
        entity.setEnhancementConfig(req.getEnhancement() != null ? req.getEnhancement() : new EnhancementConfig());
        entity.setPdfConfig(req.getPdf() != null ? req.getPdf() : new PdfConfig());
        entity.setRecallConfig(normalizeForStorage(req.getRecall()));
    }

    private DatasetParseConfigResponse assembleResponse(DatasetParseConfig entity) {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(entity.getChunkingConfig() != null ? entity.getChunkingConfig() : new ChunkingConfig());
        resp.setSparseEmbeddingConfigId(entity.getSparseEmbeddingConfigId());
        resp.setDenseEmbeddingConfigId(entity.getDenseEmbeddingConfigId());
        resp.setEnhancement(entity.getEnhancementConfig() != null
            ? entity.getEnhancementConfig() : new EnhancementConfig());
        resp.setPdf(entity.getPdfConfig() != null ? entity.getPdfConfig() : new PdfConfig());
        resp.setRecall(fillRecallDefaults(entity.getRecallConfig()));
        return resp;
    }

    private DatasetParseConfigResponse emptyResponse() {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(new ChunkingConfig());
        resp.setSparseEmbeddingConfigId(null);
        resp.setDenseEmbeddingConfigId(null);
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
        validateRerankTopN(source.getRerankTopN());
        return normalized;
    }

    private void applyModelBindings(Long userId, DatasetParseConfig target, DatasetParseConfig existing,
                                    UpdateDatasetParseConfigRequest request) {
        Long sparse = request.getSparseEmbeddingConfigId() != null
            ? request.getSparseEmbeddingConfigId()
            : existing != null ? existing.getSparseEmbeddingConfigId() : null;
        Long dense = request.getDenseEmbeddingConfigId() != null
            ? request.getDenseEmbeddingConfigId()
            : existing != null ? existing.getDenseEmbeddingConfigId() : null;
        embeddingConfigValidator.validateBindingPair(userId, sparse, dense);
        target.setSparseEmbeddingConfigId(sparse);
        target.setDenseEmbeddingConfigId(dense);
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
            if (!ALLOWED_RECALL_SOURCES.contains(trimmed)) {
                throw new BusinessException(400, "recall_enabled_sources 仅支持 bm25/sparse/dense", 400);
            }
            normalized.add(trimmed);
        }
        return new ArrayList<>(normalized);
    }

    private void validateRerankTopN(Integer rerankTopN) {
        if (rerankTopN != null && rerankTopN <= 0) {
            throw new BusinessException(400, "rerank_top_n 必须为正整数", 400);
        }
    }

    private RecallConfig fillRecallDefaults(RecallConfig source) {
        RecallConfig filled = source != null ? copyRecall(source) : new RecallConfig();
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

    private RecallConfig copyRecall(RecallConfig source) {
        RecallConfig copy = new RecallConfig();
        copy.setRecallResultLimit(source.getRecallResultLimit());
        copy.setRecallContextTokenBudget(source.getRecallContextTokenBudget());
        copy.setSparseTopK(source.getSparseTopK());
        copy.setSparseScoreThreshold(source.getSparseScoreThreshold());
        copy.setDenseTopK(source.getDenseTopK());
        copy.setDenseScoreThreshold(source.getDenseScoreThreshold());
        copy.setRecallEnabledSources(source.getRecallEnabledSources());
        copy.setRerankTopN(source.getRerankTopN());
        copy.setRecallStrict(source.getRecallStrict());
        return copy;
    }
}
