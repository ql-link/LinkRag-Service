package com.qingluo.link.service.impl;

import com.qingluo.link.core.dto.request.CreateConversationRequest;
import com.qingluo.link.core.dto.request.SaveMessageRequest;
import com.qingluo.link.core.dto.response.ConversationDTO;
import com.qingluo.link.core.dto.response.MessageDTO;
import com.qingluo.link.core.dto.response.PageResult;
import com.qingluo.link.core.enums.ErrorCode;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.model.entity.ChatConversation;
import com.qingluo.link.model.entity.ChatMessage;
import com.qingluo.link.model.entity.UserLLMConfig;
import com.qingluo.link.service.ChatService;
import com.qingluo.link.service.mapper.ChatConversationMapper;
import com.qingluo.link.service.mapper.ChatMessageMapper;
import com.qingluo.link.service.mapper.UserLLMConfigMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对话服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final UserLLMConfigMapper configMapper;

    @Override
    public ConversationDTO createConversation(String userId, CreateConversationRequest request) {
        String lastModelName = null;
        if (request.getLastConfigId() != null) {
            UserLLMConfig config = configMapper.selectById(request.getLastConfigId());
            if (config != null) {
                lastModelName = config.getModelName();
            }
        }

        ChatConversation conversation = ChatConversation.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .lastConfigId(request.getLastConfigId())
            .lastModelName(lastModelName)
            .title(request.getTitle())
            .isPinned(false)
            .isDeleted(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        conversationMapper.insert(conversation);

        return toConversationDTO(conversation);
    }

    @Override
    public PageResult<ConversationDTO> listConversations(String userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        var conversations = conversationMapper.selectByUserId(userId);
        PageInfo<ChatConversation> pageInfo = new PageInfo<>(conversations);

        return PageResult.<ConversationDTO>builder()
            .items(pageInfo.getList().stream().map(this::toConversationDTO).toList())
            .total(pageInfo.getTotal())
            .page(page)
            .pageSize(pageSize)
            .build();
    }

    @Override
    public PageResult<MessageDTO> getMessageHistory(String userId, String conversationId, int page, int pageSize) {
        // 验证用户有权访问该对话
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND, "对话不存在");
        }

        PageHelper.startPage(page, pageSize);
        var messages = messageMapper.selectByConversationId(conversationId);
        PageInfo<ChatMessage> pageInfo = new PageInfo<>(messages);

        return PageResult.<MessageDTO>builder()
            .items(pageInfo.getList().stream().map(this::toMessageDTO).toList())
            .total(pageInfo.getTotal())
            .page(page)
            .pageSize(pageSize)
            .build();
    }

    @Override
    public MessageDTO saveMessage(String userId, String conversationId, SaveMessageRequest request) {
        // 验证用户有权访问该对话
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND, "对话不存在");
        }

        // 更新对话的最后使用配置
        UserLLMConfig config = configMapper.selectById(request.getConfigId());
        if (config != null) {
            conversation.setLastConfigId(request.getConfigId());
            conversation.setLastModelName(config.getModelName());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }

        ChatMessage message = ChatMessage.builder()
            .id(UUID.randomUUID().toString())
            .conversationId(conversationId)
            .configId(request.getConfigId())
            .modelName(config != null ? config.getModelName() : null)
            .role(request.getRole())
            .content(request.getContent())
            .tokenCount(request.getTokenCount() != null ? request.getTokenCount() : 0)
            .createdAt(LocalDateTime.now())
            .build();

        messageMapper.insert(message);

        return toMessageDTO(message);
    }

    @Override
    public void deleteConversation(String userId, String conversationId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.CONVERSATION_NOT_FOUND, "对话不存在");
        }

        conversationMapper.deleteById(conversationId);
    }

    private ConversationDTO toConversationDTO(ChatConversation c) {
        return ConversationDTO.builder()
            .id(c.getId())
            .title(c.getTitle())
            .lastConfigId(c.getLastConfigId())
            .lastModelName(c.getLastModelName())
            .isPinned(c.getIsPinned())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }

    private MessageDTO toMessageDTO(ChatMessage m) {
        return MessageDTO.builder()
            .id(m.getId())
            .conversationId(m.getConversationId())
            .configId(m.getConfigId())
            .modelName(m.getModelName())
            .role(m.getRole())
            .content(m.getContent())
            .tokenCount(m.getTokenCount())
            .createdAt(m.getCreatedAt())
            .build();
    }
}