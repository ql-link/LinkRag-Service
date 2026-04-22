package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.ChatMessage;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对话服务实现
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final DatasetMapper datasetMapper;

    @Override
    @Transactional
    public ConversationDTO createConversation(Long userId, CreateConversationRequest request) {
        assertOwnedDataset(userId, request.getDatasetId());

        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setDatasetId(request.getDatasetId());
        conversation.setTitle(request.getTitle() != null ? request.getTitle() : "新对话");
        conversation.setLastConfigId(request.getLastConfigId());
        conversation.setIsPinned(false);
        conversation.setIsDeleted(false);

        conversationMapper.insert(conversation);

        return toDTO(conversation);
    }

    @Override
    public PageResult<ConversationDTO> getConversations(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<ChatConversation> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getIsDeleted, false)
                .orderByDesc(ChatConversation::getIsPinned)
                .orderByDesc(ChatConversation::getUpdatedAt)
        );

        PageInfo<ChatConversation> pageInfo = new PageInfo<>(conversations);

        List<ConversationDTO> dtos = conversations.stream().map(this::toDTO).toList();

        return new PageResult<>(dtos, pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public PageResult<MessageDTO> getMessages(Long userId, Long conversationId, int page, int pageSize) {
        // 验证对话归属
        ChatConversation conversation = conversationMapper.selectOne(
            new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getIsDeleted, false)
        );

        if (conversation == null) {
            throw NotFoundException.conversationNotFound();
        }

        PageHelper.startPage(page, pageSize);
        List<ChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt)
        );

        PageInfo<ChatMessage> pageInfo = new PageInfo<>(messages);

        List<MessageDTO> dtos = messages.stream().map(this::toDTO).toList();

        return new PageResult<>(dtos, pageInfo.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        // 验证对话归属
        ChatConversation conversation = conversationMapper.selectOne(
            new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getIsDeleted, false)
        );

        if (conversation == null) {
            throw NotFoundException.conversationNotFound();
        }

        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
            .eq(ChatConversation::getId, conversationId)
            .set(ChatConversation::getIsDeleted, true));
    }

    private ConversationDTO toDTO(ChatConversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setTitle(conversation.getTitle());
        dto.setDatasetId(conversation.getDatasetId());
        dto.setLastConfigId(conversation.getLastConfigId());
        dto.setLastModelName(conversation.getLastModelName());
        dto.setIsPinned(conversation.getIsPinned());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());
        return dto;
    }

    private MessageDTO toDTO(ChatMessage message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setConfigId(message.getConfigId());
        dto.setModelName(message.getModelName());
        dto.setTokenCount(message.getTokenCount());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }
}
