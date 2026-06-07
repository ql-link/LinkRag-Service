package com.qingluo.link.core.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 认证上下文
 * 从 Token 获取当前用户 ID
 */
public class AuthContext {

    /**
     * 获取当前登录用户 ID；未登录或无请求上下文时返回 {@code null}（绝不抛异常）。
     *
     * <p>定位为“尽力获取”的旁路 getter：审计、访问日志等场景需要它在任何情况下都安全。
     * 未登录时 {@code getLoginId()} 抛 {@code NotLoginException}，无 Web 上下文（如纯单测）时
     * 还会抛 {@code SaTokenContextException}，故统一捕获并降级为 {@code null}。需要强校验登录态
     * 的业务路径请用 {@link #getLoginUserIdOrThrow()}。</p>
     */
    public static Long getCurrentUserId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            if (loginId instanceof Long) {
                return (Long) loginId;
            }
            return Long.parseLong(loginId.toString());
        } catch (Exception e) {
            return null;
        }
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