package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.UserProfileDTO;

/**
 * 用户信息缓存服务
 * key: user:info:{userId}  TTL: 7天
 */
public interface UserCacheService {

    void put(Long userId, UserProfileDTO dto);

    /** 缓存未命中返回 null */
    UserProfileDTO get(Long userId);

    /** 双删驱逐 */
    void evict(Long userId);
}
