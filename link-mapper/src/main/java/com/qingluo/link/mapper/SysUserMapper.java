package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名或邮箱匹配登录账号。
     */
    SysUser selectByAccount(@Param("account") String account);

    /**
     * 按用户名查询用户。
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 按邮箱查询用户。
     */
    SysUser selectByEmail(@Param("email") String email);

    /**
     * 查询除当前用户外是否存在相同邮箱。
     */
    SysUser selectByEmailExcludingUserId(@Param("email") String email, @Param("userId") Long userId);
}
