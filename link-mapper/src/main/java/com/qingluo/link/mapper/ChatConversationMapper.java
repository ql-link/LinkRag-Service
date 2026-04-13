package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话 Mapper
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}