package com.qingluo.link.service.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.dto.request.CreateConversationRequest;
import com.qingluo.link.core.dto.request.SaveMessageRequest;
import com.qingluo.link.core.dto.response.*;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 对话控制器
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @SaCheckLogin
    @PostMapping("/conversations")
    public Result<ConversationDTO> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        String userId = AuthContext.getCurrentUserId();
        ConversationDTO result = chatService.createConversation(userId, request);
        return Result.created(result);
    }

    @SaCheckLogin
    @GetMapping("/conversations")
    public Result<PageResult<ConversationDTO>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = AuthContext.getCurrentUserId();
        PageResult<ConversationDTO> result = chatService.listConversations(userId, page, pageSize);
        return Result.success(result);
    }

    @SaCheckLogin
    @GetMapping("/conversations/{id}/messages")
    public Result<PageResult<MessageDTO>> getMessageHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        String userId = AuthContext.getCurrentUserId();
        PageResult<MessageDTO> result = chatService.getMessageHistory(userId, id, page, pageSize);
        return Result.success(result);
    }

    @SaCheckLogin
    @PostMapping("/conversations/{id}/messages")
    public Result<MessageDTO> saveMessage(
            @PathVariable String id,
            @Valid @RequestBody SaveMessageRequest request) {
        String userId = AuthContext.getCurrentUserId();
        MessageDTO result = chatService.saveMessage(userId, id, request);
        return Result.created(result);
    }

    @SaCheckLogin
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable String id) {
        String userId = AuthContext.getCurrentUserId();
        chatService.deleteConversation(userId, id);
        return Result.success(null);
    }
}