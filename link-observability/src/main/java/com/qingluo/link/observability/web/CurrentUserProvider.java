package com.qingluo.link.observability.web;

import javax.servlet.http.HttpServletRequest;

/**
 * Optional bridge for access logs to read the current application user without depending on auth modules.
 */
@FunctionalInterface
public interface CurrentUserProvider {

    String ANONYMOUS_USER = "-";

    String currentUserIdOrDash(HttpServletRequest request);

    static CurrentUserProvider anonymous() {
        return request -> ANONYMOUS_USER;
    }
}
