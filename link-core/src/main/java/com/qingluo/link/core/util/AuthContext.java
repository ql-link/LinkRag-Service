package com.qingluo.link.core.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 认证上下文
 * 从 Token 获取当前用户 ID
 */
public class AuthContext {

    /**
     * 获取当前登录用户 ID
     */
    public static Long getCurrentUserId() {
        Object loginId = StpUtil.getLoginId();
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Long) {
            return (Long) loginId;
        }
        return Long.parseLong(loginId.toString());
    }

    /**
     * 获取当前登录用户 ID（如果未登录抛出异常）
     */
    public static Long getLoginUserIdOrThrow() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("用户未登录");
        }
        return userId;
    }

    /**
     * 检查是否已登录
     */
    public static boolean isLoggedIn() {
        return StpUtil.isLogin();
    }
}