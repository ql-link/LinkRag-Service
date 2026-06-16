package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
import com.qingluo.link.service.DatasetParseConfigService;
import com.qingluo.link.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据集解析/检索配置管理实现：Java 纯管理，不持有默认值、不做消费式读取与兜底。
 */
@Service
@RequiredArgsConstructor
public class DatasetParseConfigServiceImpl implements DatasetParseConfigService {

    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final DatasetService datasetService;

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
            return overwriteRow(existing.getId(), request);
        }

        DatasetParseConfig created = new DatasetParseConfig();
        created.setUserId(userId);
        created.setDatasetId(datasetId);
        created.setIsActive(true);
        applyConfigs(created, request);
        try {
            datasetParseConfigMapper.insert(created);
            return assembleResponse(created);
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一键 uk_user_dataset 撞行：转为更新已存在的行。
            DatasetParseConfig concurrent = selectByOwner(userId, datasetId);
            return overwriteRow(concurrent.getId(), request);
        }
    }

    /**
     * 整行覆盖四类配置。仅 set 主键与四类列，刻意不带 created_at/updated_at——
     * 若把 select 出的旧 updated_at 一并 updateById，会以旧值显式赋值，抑制 DB 的
     * ON UPDATE CURRENT_TIMESTAMP 自动刷新，导致「最后更新时间」不变。
     */
    private DatasetParseConfigResponse overwriteRow(Long id, UpdateDatasetParseConfigRequest request) {
        DatasetParseConfig update = new DatasetParseConfig();
        update.setId(id);
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
        entity.setRecallConfig(req.getRecall() != null ? req.getRecall() : new RecallConfig());
    }

    private DatasetParseConfigResponse assembleResponse(DatasetParseConfig entity) {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(entity.getChunkingConfig() != null ? entity.getChunkingConfig() : new ChunkingConfig());
        resp.setEnhancement(entity.getEnhancementConfig() != null
            ? entity.getEnhancementConfig() : new EnhancementConfig());
        resp.setPdf(entity.getPdfConfig() != null ? entity.getPdfConfig() : new PdfConfig());
        resp.setRecall(entity.getRecallConfig() != null ? entity.getRecallConfig() : new RecallConfig());
        return resp;
    }

    private DatasetParseConfigResponse emptyResponse() {
        DatasetParseConfigResponse resp = new DatasetParseConfigResponse();
        resp.setChunking(new ChunkingConfig());
        resp.setEnhancement(new EnhancementConfig());
        resp.setPdf(new PdfConfig());
        resp.setRecall(new RecallConfig());
        return resp;
    }
}
