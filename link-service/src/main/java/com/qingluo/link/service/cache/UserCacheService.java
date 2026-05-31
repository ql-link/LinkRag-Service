package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.UserProfileDTO;

import java.util.function.Supplier;

/**
 * 用户信息缓存服务
 * key: user:info:{userId}  TTL: 7天
 */
public interface UserCacheService {

    void put(Long userId, UserProfileDTO dto);

    /** 缓存未命中返回 null */
    UserProfileDTO get(Long userId);

    /**
     * Read-through entry for cache owners that already know how to回源数据库.
     */
    UserProfileDTO getOrLoad(Long userId, Supplier<UserProfileDTO> loader);

    /** 写库成功后的同步删缓存 */
    void evict(Long userId);
}
