package com.qingluo.link.core.observability;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.observability.web.CurrentUserProvider;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Bridges the auth context into observability without making observability depend on core.
 */
@Component
public class AuthCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String currentUserIdOrDash(HttpServletRequest request) {
        Long userId = AuthContext.getCurrentUserId();
        if (userId != null) {
            return userId.toString();
        }
        Object loginId = loginIdFromToken(request);
        return loginId == null ? ANONYMOUS_USER : loginId.toString();
    }

    private Object loginIdFromToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String token = request.getHeader(StpUtil.getTokenName());
            if (!StringUtils.hasText(token)) {
                token = request.getHeader("satoken");
            }
            return StringUtils.hasText(token) ? StpUtil.getLoginIdByToken(token) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
