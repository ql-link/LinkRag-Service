package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.request.UpdateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.DatasetService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据集服务实现，负责数据集的创建、查询和级联删除。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetServiceImpl implements DatasetService {

    private static final String DATASET_STATUS_ACTIVE = "ACTIVE";

    private final DatasetMapper datasetMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final IOssService ossService;
    private final PrivateFileResolver privateFileResolver;

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
     * 删除数据集及其关联文件、会话和消息记录。
     */
    public void delete(Long userId, Long datasetId) {
        Dataset dataset = getOwnedDataset(userId, datasetId);

        List<DocumentOriginalFile> files = documentOriginalFileMapper.selectList(new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getDatasetId, dataset.getId()));
        for (DocumentOriginalFile file : files) {
            if (StringUtils.hasText(file.getObjectKey())) {
                boolean deleted = ossService.deleteFile(OssSavePlaceEnum.PRIVATE, file.getObjectKey());
                if (!deleted) {
                    log.error("Delete dataset oss object failed, userId={}, datasetId={}, objectKey={}",
                        userId, datasetId, file.getObjectKey());
                    throw new BusinessException(500, "删除数据集原文件失败，请稍后重试", 500);
                }
                try {
                    privateFileResolver.evictPrivateFile(file.getObjectKey());
                } catch (RuntimeException e) {
                    log.warn("Evict dataset private file cache failed after oss delete, userId={}, datasetId={}, objectKey={}",
                        userId, datasetId, file.getObjectKey(), e);
                }
            }
        }

        try {
            List<ChatConversation> conversations = chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getDatasetId, dataset.getId()));
            for (ChatConversation conversation : conversations) {
                chatMessageMapper.delete(new LambdaQueryWrapper<com.qingluo.link.model.dto.entity.ChatMessage>()
                    .eq(com.qingluo.link.model.dto.entity.ChatMessage::getConversationId, conversation.getId()));
            }

            documentOriginalFileMapper.delete(new LambdaQueryWrapper<DocumentOriginalFile>()
                .eq(DocumentOriginalFile::getDatasetId, dataset.getId()));
            chatConversationMapper.delete(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getDatasetId, dataset.getId()));
            datasetMapper.deleteById(dataset.getId());
        } catch (RuntimeException e) {
            log.error("Delete dataset database records failed after oss cleanup, userId={}, datasetId={}",
                userId, datasetId, e);
            throw new BusinessException(500, "数据集原始对象已删除，但数据库记录删除失败，请尽快补偿处理", 500);
        }
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
