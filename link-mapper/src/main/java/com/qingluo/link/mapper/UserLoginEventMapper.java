package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.UserLoginEvent;
import com.qingluo.link.model.dto.response.UserDailyCountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserLoginEventMapper extends BaseMapper<UserLoginEvent> {
    long countDistinctUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<UserDailyCountDTO> selectDailyActiveBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
