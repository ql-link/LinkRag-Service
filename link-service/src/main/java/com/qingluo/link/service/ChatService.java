package com.qingluo.link.service;

import com.qingluo.link.core.dto.request.CreateConversationRequest;
import com.qingluo.link.core.dto.request.SaveMessageRequest;
import com.qingluo.link.core.dto.response.ConversationDTO;
import com.qingluo.link.core.dto.response.MessageDTO;
import com.qingluo.link.core.dto.response.PageResult;

/**
 * 对话服务接口
 */
public interface ChatService {

    /**
     * 创建新对话
     */
    ConversationDTO createConversation(String userId, CreateConversationRequest request);

    /**
     * 获取用户的对话列表（分页）
     */
    PageResult<ConversationDTO> listConversations(String userId, int page, int pageSize);

    /**
     * 获取对话的历史消息（分页）
     */
    PageResult<MessageDTO> getMessageHistory(String userId, String conversationId, int page, int pageSize);

    /**
     * 保存消息
     */
    MessageDTO saveMessage(String userId, String conversationId, SaveMessageRequest request);

    /**
     * 删除对话（软删除）
     */
    void deleteConversation(String userId, String conversationId);
}