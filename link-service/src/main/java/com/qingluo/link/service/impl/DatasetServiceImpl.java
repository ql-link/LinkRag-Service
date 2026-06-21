package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.request.UpdateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.DatasetService;
import com.qingluo.link.service.delete.DocumentDeleteNotifier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 数据集服务实现，负责数据集的创建、查询和级联删除。
 */
@Service
@RequiredArgsConstructor
public class DatasetServiceImpl implements DatasetService {

    private static final String DATASET_STATUS_ACTIVE = "ACTIVE";
    private static final List<String> DEFAULT_RECALL_ENABLED_SOURCES = List.of("bm25", "sparse", "dense");

    private final DatasetMapper datasetMapper;
    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentDeleteNotifier deleteNotifier;

    @Override
    @Transactional
    /**
     * 创建用户数据集。
     */
    public DatasetDTO create(Long userId, CreateDatasetRequest request) {
        Dataset dataset = new Dataset();
        dataset.setUserId(userId);
        dataset.setName(request.getName().trim());
        dataset.setDescription(request.getDescription());
        dataset.setStatus(DATASET_STATUS_ACTIVE);
        try {
            datasetMapper.insert(dataset);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "当前用户下已存在同名数据集", 400);
        }
        insertDefaultParseConfig(userId, dataset.getId());
        return toDTO(dataset);
    }

    @Override
    /**
     * 分页查询当前用户的数据集列表。
     */
    public PageResult<DatasetDTO> list(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Dataset> datasets = datasetMapper.selectList(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getUserId, userId)
            .orderByDesc(Dataset::getUpdatedAt));
        PageInfo<Dataset> pageInfo = new PageInfo<>(datasets);
        return new PageResult<>(datasets.stream().map(this::toDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    /**
     * 查询指定数据集详情。
     */
    public DatasetDTO detail(Long userId, Long datasetId) {
        return toDTO(getOwnedDataset(userId, datasetId));
    }

    @Override
    @Transactional
    /**
     * 更新当前用户拥有的数据集基础信息。
     */
    public DatasetDTO update(Long userId, Long datasetId, UpdateDatasetRequest request) {
        getOwnedDataset(userId, datasetId);
        Dataset update = new Dataset();
        update.setId(datasetId);
        boolean changed = false;

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException(400, "数据集名称不能为空", 400);
            }
            update.setName(name);
            changed = true;
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription().trim());
            changed = true;
        }
        if (!changed) {
            throw new BusinessException(400, "请至少提供一个需要更新的字段", 400);
        }

        try {
            datasetMapper.updateById(update);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "当前用户下已存在同名数据集", 400);
        }
        return toDTO(getOwnedDataset(userId, datasetId));
    }

    @Override
    @Transactional
    /**
     * 隐性删除数据集：软删名下原文件与数据集行（保留 OSS 原文件对象、不物理删行），
     * 物理删名下会话与消息（衍生数据不保留），提交后预留通知 Python 删除解析域衍生产物（占位）。
     */
    public void delete(Long userId, Long datasetId) {
        Dataset dataset = getOwnedDataset(userId, datasetId);

        // 软删名下原文件：不删 OSS 对象、不物理删行；deleted_seq 置为各行自身 id，使死行退出唯一键“活名额”，
        // 支持删后同名重传（实体带 @TableLogic，MP 对 wrapper update 自动追加 is_deleted=0，只软删当前活行）。
        documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getDatasetId, dataset.getId())
            .set(DocumentOriginalFile::getIsDeleted, true)
            .setSql("deleted_seq = id"));

        // 会话与消息属衍生数据，一律物理删（ChatConversation 已去 @TableLogic，delete 即物理删）。
        List<ChatConversation> conversations = chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
            .eq(ChatConversation::getDatasetId, dataset.getId()));
        for (ChatConversation conversation : conversations) {
            chatMessageMapper.delete(new LambdaQueryWrapper<com.qingluo.link.model.dto.entity.ChatMessage>()
                .eq(com.qingluo.link.model.dto.entity.ChatMessage::getConversationId, conversation.getId()));
        }
        chatConversationMapper.delete(new LambdaQueryWrapper<ChatConversation>()
            .eq(ChatConversation::getDatasetId, dataset.getId()));

        // 软删数据集行本身（保留空壳便于追溯/恢复）；deleted_seq 置为自身 id。
        datasetMapper.update(null, new LambdaUpdateWrapper<Dataset>()
            .eq(Dataset::getId, dataset.getId())
            .set(Dataset::getIsDeleted, true)
            .set(Dataset::getDeletedSeq, dataset.getId()));

        // 事务提交后再通知 Python 删衍生产物（dataset 范围，Python 按 datasetId 删名下全部）；回滚则不通知，避免对未真正删除的数据误通知。
        notifyDatasetDeletedAfterCommit(datasetId, userId);
    }

    /**
     * 删除事务提交后通知 Python 删除该数据集名下全部衍生产物（dataset 范围）；处于事务中则注册 afterCommit
     * （回滚不发），无事务时（如单元测试）直接调用。沿用上传链路的 afterCommit 模式。
     */
    private void notifyDatasetDeletedAfterCommit(Long datasetId, Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNotifier.notifyDatasetDeleted(datasetId, userId);
                }
            });
        } else {
            deleteNotifier.notifyDatasetDeleted(datasetId, userId);
        }
    }

    private void insertDefaultParseConfig(Long userId, Long datasetId) {
        DatasetParseConfig config = new DatasetParseConfig();
        config.setUserId(userId);
        config.setDatasetId(datasetId);
        config.setChunkingConfig(new ChunkingConfig());
        config.setEnhancementConfig(new EnhancementConfig());
        config.setPdfConfig(new PdfConfig());
        config.setRecallConfig(defaultRecallConfig());
        config.setIsActive(true);
        datasetParseConfigMapper.insert(config);
    }

    private RecallConfig defaultRecallConfig() {
        RecallConfig recall = new RecallConfig();
        recall.setRecallEnabledSources(DEFAULT_RECALL_ENABLED_SOURCES);
        recall.setRerankTopN(8);
        recall.setRecallStrict(false);
        return recall;
    }

    /**
     * 查询当前用户拥有的数据集，不存在则抛出异常。
     */
    private Dataset getOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
        return dataset;
    }

    /**
     * 将数据集实体转换为 DTO。
     */
    private DatasetDTO toDTO(Dataset dataset) {
        DatasetDTO dto = new DatasetDTO();
        dto.setId(dataset.getId());
        dto.setName(dataset.getName());
        dto.setDescription(dataset.getDescription());
        dto.setStatus(dataset.getStatus());
        dto.setCreatedAt(dataset.getCreatedAt());
        dto.setUpdatedAt(dataset.getUpdatedAt());
        return dto;
    }
}
