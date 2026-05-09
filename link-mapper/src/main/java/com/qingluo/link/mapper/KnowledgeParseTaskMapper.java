package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件解析任务持久化入口。
 */
@Mapper
public interface KnowledgeParseTaskMapper extends BaseMapper<KnowledgeParseTask> {
}
