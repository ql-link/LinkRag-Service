package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.request.SendMessageRequest;
import com.qingluo.link.model.dto.request.UpdateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;

/**
 * 对话服务接口
 */
public interface ChatService {

    /**
     * 创建对话
     */
    ConversationDTO createConversation(Long userId, CreateConversationRequest request);

    /**
     * 获取对话列表
     */
    PageResult<ConversationDTO> getConversations(Long userId, int page, int pageSize);

    /**
     * 获取对话消息
     */
    PageResult<MessageDTO> getMessages(Long userId, Long conversationId, int page, int pageSize);

    /**
     * 更新对话信息
     */
    ConversationDTO updateConversation(Long userId, Long conversationId, UpdateConversationRequest request);

    /**
     * 发送消息
     */
    MessageDTO sendMessage(Long userId, Long conversationId, SendMessageRequest request);

    /**
     * 删除对话
     */
    void deleteConversation(Long userId, Long conversationId);
}
