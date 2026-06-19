package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.request.UpdateConversationRequest;
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
import org.springframework.util.StringUtils;

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
    /**
     * 为指定数据集创建新的会话。
     */
    public ConversationDTO createConversation(Long userId, CreateConversationRequest request) {
        assertOwnedDataset(userId, request.getDatasetId());

        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setDatasetId(request.getDatasetId());
        conversation.setTitle(request.getTitle() != null ? request.getTitle() : "新对话");
        conversation.setLastConfigId(request.getLastConfigId());
        conversation.setIsPinned(false);

        conversationMapper.insert(conversation);

        return toDTO(conversation);
    }

    @Override
    /**
     * 分页查询用户可见的会话列表。
     */
    public PageResult<ConversationDTO> getConversations(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<ChatConversation> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getIsPinned)
                .orderByDesc(ChatConversation::getUpdatedAt)
        );

        PageInfo<ChatConversation> pageInfo = new PageInfo<>(conversations);

        List<ConversationDTO> dtos = conversations.stream().map(this::toDTO).toList();

        return new PageResult<>(dtos, pageInfo.getTotal(), page, pageSize);
    }

    @Override
    /**
     * 分页查询会话下的消息明细。
     */
    public PageResult<MessageDTO> getMessages(Long userId, Long conversationId, int page, int pageSize) {
        // 验证对话归属
        getOwnedConversation(userId, conversationId);

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
    /**
     * 更新会话标题、置顶状态等基础信息。
     */
    public ConversationDTO updateConversation(Long userId, Long conversationId, UpdateConversationRequest request) {
        ChatConversation conversation = getOwnedConversation(userId, conversationId);

        boolean changed = false;
        if (request.getTitle() != null) {
            String title = request.getTitle().trim();
            if (!StringUtils.hasText(title)) {
                throw new BusinessException(400, "对话标题不能为空", 400);
            }
            conversation.setTitle(title);
            changed = true;
        }

        if (request.getIsPinned() != null) {
            conversation.setIsPinned(request.getIsPinned());
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(400, "请至少提供一个需要更新的字段", 400);
        }

        conversationMapper.updateById(conversation);
        return toDTO(conversation);
    }

    @Override
    @Transactional
    /**
     * 删除用户会话及其消息。
     */
    public void deleteConversation(Long userId, Long conversationId) {
        getOwnedConversation(userId, conversationId);

        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    /**
     * 将会话实体转换为会话 DTO。
     */
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

    /**
     * 将消息实体转换为消息 DTO。
     */
    private MessageDTO toDTO(ChatMessage message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setQuery(message.getQuery());
        dto.setAnswer(message.getAnswer());
        dto.setConfigId(message.getConfigId());
        dto.setModelName(message.getModelName());
        dto.setReferences(message.getReferences());
        dto.setStatus(message.getStatus());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    /**
     * 校验并返回当前用户拥有的会话。
     */
    private ChatConversation getOwnedConversation(Long userId, Long conversationId) {
        ChatConversation conversation = conversationMapper.selectOne(
            new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
        );
        if (conversation == null) {
            throw NotFoundException.conversationNotFound();
        }
        return conversation;
    }

    /**
     * 校验数据集是否归属于当前用户。
     */
    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }
}
