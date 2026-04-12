package com.qingluo.link.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * SysUser Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUser selectByUsername(@Param("username") String username);

    int updateLastLoginTime(@Param("userId") String userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int insert(SysUser user);
}