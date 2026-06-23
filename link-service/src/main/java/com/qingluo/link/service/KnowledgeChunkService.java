package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.ChunkDetailDTO;

import java.util.List;

/**
 * 知识库 Chunk 查询服务。
 */
public interface KnowledgeChunkService {

    /**
     * 批量查询当前用户可访问的 ACTIVE Chunk 详情。
     */
    List<ChunkDetailDTO> getChunkDetails(Long userId, List<String> chunkIds);
}
