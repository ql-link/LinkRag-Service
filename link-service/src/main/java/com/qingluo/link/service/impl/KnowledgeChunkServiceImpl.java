package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.KbDocumentChunkMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.KbDocumentChunk;
import com.qingluo.link.model.dto.response.ChunkDetailDTO;
import com.qingluo.link.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库 Chunk 查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    private static final String ACTIVE = "ACTIVE";

    private final KbDocumentChunkMapper chunkMapper;
    private final DocumentOriginalFileMapper originalFileMapper;

    @Override
    public List<ChunkDetailDTO> getChunkDetails(Long userId, List<String> chunkIds) {
        List<String> orderedChunkIds = normalizeChunkIds(chunkIds);
        if (orderedChunkIds.isEmpty()) {
            return List.of();
        }

        List<KbDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbDocumentChunk>()
            .in(KbDocumentChunk::getChunkId, orderedChunkIds)
            .eq(KbDocumentChunk::getUserId, userId)
            .eq(KbDocumentChunk::getLifecycleStatus, ACTIVE));
        if (chunks.isEmpty()) {
            return List.of();
        }

        Map<String, KbDocumentChunk> chunkById = chunks.stream()
            .collect(Collectors.toMap(KbDocumentChunk::getChunkId, Function.identity(), (left, right) -> left));
        Map<Long, String> filenameByDocId = loadFilenameByDocId(userId, chunks);

        return orderedChunkIds.stream()
            .map(chunkById::get)
            .filter(chunk -> chunk != null && StringUtils.hasText(chunk.getContent()))
            .map(chunk -> toDTO(chunk, filenameByDocId.get(chunk.getDocId())))
            .toList();
    }

    private List<String> normalizeChunkIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (String chunkId : chunkIds) {
            if (StringUtils.hasText(chunkId)) {
                unique.putIfAbsent(chunkId.trim(), Boolean.TRUE);
            }
        }
        return List.copyOf(unique.keySet());
    }

    private Map<Long, String> loadFilenameByDocId(Long userId, List<KbDocumentChunk> chunks) {
        List<Long> docIds = chunks.stream()
            .map(KbDocumentChunk::getDocId)
            .filter(docId -> docId != null)
            .distinct()
            .toList();
        if (docIds.isEmpty()) {
            return Map.of();
        }

        return originalFileMapper.selectList(new LambdaQueryWrapper<DocumentOriginalFile>()
                .in(DocumentOriginalFile::getId, docIds)
                .eq(DocumentOriginalFile::getUserId, userId))
            .stream()
            .collect(Collectors.toMap(DocumentOriginalFile::getId, DocumentOriginalFile::getOriginalFilename,
                (left, right) -> left));
    }

    private ChunkDetailDTO toDTO(KbDocumentChunk chunk, String filename) {
        ChunkDetailDTO dto = new ChunkDetailDTO();
        dto.setChunkId(chunk.getChunkId());
        dto.setDocumentId(chunk.getDocId());
        dto.setFileName(StringUtils.hasText(filename) ? filename : "文档 #" + chunk.getDocId());
        dto.setContent(chunk.getContent());
        dto.setScore(null);
        return dto;
    }
}
