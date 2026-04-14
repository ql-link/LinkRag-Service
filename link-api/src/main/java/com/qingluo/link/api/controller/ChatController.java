package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.ChatService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 对话控制器
 * <p>提供会话创建、消息管理、对话删除等功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @SaCheckLogin
    public Result<ConversationDTO> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.createConversation(userId, request));
    }

    @GetMapping
    @SaCheckLogin
    public Result<PageResult<ConversationDTO>> getConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.getConversations(userId, page, pageSize));
    }

    @GetMapping("/{id}/messages")
    @SaCheckLogin
    public Result<PageResult<MessageDTO>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.getMessages(userId, id, page, pageSize));
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        chatService.deleteConversation(userId, id);
        return Result.ok(null);
    }
}
