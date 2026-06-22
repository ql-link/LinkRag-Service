package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.KbDocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Chunk 只读 Mapper。
 */
@Mapper
public interface KbDocumentChunkMapper extends BaseMapper<KbDocumentChunk> {
}
