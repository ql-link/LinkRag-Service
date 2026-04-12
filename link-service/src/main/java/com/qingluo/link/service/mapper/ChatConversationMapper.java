package com.qingluo.link.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

    List<ChatConversation> selectByUserId(@Param("userId") String userId);

    int deleteById(@Param("id") String id);

    int updateById(ChatConversation conversation);
}