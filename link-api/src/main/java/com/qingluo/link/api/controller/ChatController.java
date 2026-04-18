package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateConversationRequest;
import com.qingluo.link.model.dto.response.ConversationDTO;
import com.qingluo.link.model.dto.response.MessageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.ChatService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "对话接口", description = "对话的创建、列表、消息历史、删除管理")
public class ChatController {

    private final ChatService chatService;

    /**
     * 创建对话
     *
     * @param request 创建信息（title, lastConfigId）
     * @return 新建的对话信息（id, title, createdAt）
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "创建对话", description = "创建一个新的对话会话，可指定初始使用的LLM配置")
    public Result<ConversationDTO> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.createConversation(userId, request));
    }

    /**
     * 获取对话列表
     *
     * @param page     页码（默认1）
     * @param pageSize 每页条数（默认20）
     * @return 对话列表（按更新时间倒序，不含已删除）
     */
    @GetMapping
    @SaCheckLogin
    @Operation(summary = "获取对话列表", description = "获取当前用户的所有对话列表，按更新时间倒序排列")
    public Result<PageResult<ConversationDTO>> getConversations(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.getConversations(userId, page, pageSize));
    }

    /**
     * 获取对话历史消息
     *
     * @param id       对话ID
     * @param page     页码（默认1）
     * @param pageSize 每页条数（默认50）
     * @return 消息列表（按创建时间正序）
     */
    @GetMapping("/{id}/messages")
    @SaCheckLogin
    @Operation(summary = "获取对话消息", description = "获取指定对话的所有消息，按创建时间正序排列")
    public Result<PageResult<MessageDTO>> getMessages(
            @Parameter(description = "对话ID") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "50") int pageSize) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(chatService.getMessages(userId, id, page, pageSize));
    }

    /**
     * 删除对话
     *
     * @param id 对话ID
     * @return 无返回内容
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "删除对话", description = "软删除对话，标记is_deleted=true，对话内容保留但列表中不显示")
    public Result<Void> deleteConversation(@Parameter(description = "对话ID") @PathVariable Long id) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        chatService.deleteConversation(userId, id);
        return Result.ok(null);
    }
}
