package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatController 控制器测试
 * TDD Red 阶段
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    @Test
    void Should_ReturnConversation_When_CreateConversationSuccess() {
        // given
        Long userId = 1L;
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("新对话");

        ConversationDTO created = new ConversationDTO();
        created.setId(1L);
        created.setTitle("新对话");
        created.setCreatedAt(LocalDateTime.now());

        when(chatService.createConversation(eq(userId), any(CreateConversationRequest.class)))
            .thenReturn(created);

        // when
        Result<ConversationDTO> result = chatController.createConversation(request);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getData().getId());
        assertEquals("新对话", result.getData().getTitle());
        verify(chatService).createConversation(eq(userId), any(CreateConversationRequest.class));
    }

    @Test
    void Should_ReturnConversationList_When_GetConversations() {
        // given
        Long userId = 1L;
        ConversationDTO conversation = new ConversationDTO();
        conversation.setId(1L);
        conversation.setTitle("对话1");

        PageResult<ConversationDTO> pageResult = new PageResult<>(List.of(conversation), 1, 1, 20);

        when(chatService.getConversations(eq(userId), eq(1), eq(20)))
            .thenReturn(pageResult);

        // when
        Result<PageResult<ConversationDTO>> result = chatController.getConversations(1, 20);

        // then
        assertNotNull(result);
        assertEquals(1, result.getData().getItems().size());
        verify(chatService).getConversations(eq(userId), eq(1), eq(20));
    }

    @Test
    void Should_ReturnMessageList_When_GetMessages() {
        // given
        Long userId = 1L;
        Long conversationId = 1L;
        MessageDTO message = new MessageDTO();
        message.setId(1L);
        message.setRole("user");
        message.setContent("你好");

        PageResult<MessageDTO> pageResult = new PageResult<>(List.of(message), 1, 1, 50);

        when(chatService.getMessages(eq(userId), eq(conversationId), eq(1), eq(50)))
            .thenReturn(pageResult);

        // when
        Result<PageResult<MessageDTO>> result = chatController.getMessages(conversationId, 1, 50);

        // then
        assertNotNull(result);
        assertEquals(1, result.getData().getItems().size());
        assertEquals("你好", result.getData().getItems().get(0).getContent());
        verify(chatService).getMessages(eq(userId), eq(conversationId), eq(1), eq(50));
    }

    @Test
    void Should_ReturnOk_When_DeleteConversationSuccess() {
        // given
        Long userId = 1L;
        Long conversationId = 1L;

        doNothing().when(chatService).deleteConversation(eq(userId), eq(conversationId));

        // when
        Result<Void> result = chatController.deleteConversation(conversationId);

        // then
        assertNotNull(result);
        verify(chatService).deleteConversation(eq(userId), eq(conversationId));
    }
}
