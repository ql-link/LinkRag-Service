package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class ChatController {

    @PostMapping
    @SaCheckLogin
    public Result<ConversationDTO> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        // TODO: 调用 ChatService.createConversation()
        return Result.success(new ConversationDTO());
    }

    @GetMapping
    @SaCheckLogin
    public Result<PageResult<ConversationDTO>> getConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // TODO: 调用 ChatService.getConversations()
        return Result.success(new PageResult<>(List.of(), 0, page, pageSize));
    }

    @GetMapping("/{id}/messages")
    @SaCheckLogin
    public Result<PageResult<MessageDTO>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        // TODO: 调用 ChatService.getMessages()
        return Result.success(new PageResult<>(List.of(), 0, page, pageSize));
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteConversation(@PathVariable Long id) {
        // TODO: 调用 ChatService.deleteConversation()
        return Result.ok(null);
    }
}