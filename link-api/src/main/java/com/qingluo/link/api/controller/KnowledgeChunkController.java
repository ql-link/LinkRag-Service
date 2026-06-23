package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.BatchChunkDetailRequest;
import com.qingluo.link.model.dto.response.ChunkDetailDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 知识库 Chunk 查询接口。
 */
@RestController
@RequestMapping("/api/v1/knowledge/chunks")
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkService knowledgeChunkService;

    @PostMapping("/batch")
    @SaCheckLogin
    public Result<List<ChunkDetailDTO>> batch(@Valid @RequestBody BatchChunkDetailRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(knowledgeChunkService.getChunkDetails(userId, request.getChunkIds()));
    }
}
