package com.qingluo.link.core.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 登录态获取工具
 */
public class AuthContext {

    /**
     * 获取当前登录用户 ID（UUID）
     * id=token 模式下，StpUtil.getLoginId() 返回的即为用户 ID
     */
    public static String getCurrentUserId() {
        return String.valueOf(StpUtil.getLoginId());
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        return String.valueOf(StpUtil.getLoginId());
    }

    /**
     * 判断当前用户是否为管理员
     */
    public static boolean isAdmin() {
        return StpUtil.hasRole("ADMIN");
    }
}