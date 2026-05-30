package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.DocumentParsePipeline;
import org.apache.ibatis.annotations.Mapper;

/**
 * document_parse_pipeline 只读 Mapper。Java 仅查询 Python 写入的流水线终态与审计字段。
 */
@Mapper
public interface DocumentParsePipelineMapper extends BaseMapper<DocumentParsePipeline> {
}
