package com.qingluo.link.api.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 公开详情异常默认 no-store；成功/304 由 Controller 覆盖为 public,no-cache。
 */
@Component
public class BlogPublicCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isPublicDetail(request)) {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 400) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicDetail(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/v1/blog/posts/")) {
            return false;
        }
        return uri.substring("/api/v1/blog/posts/".length()).indexOf('/') < 0;
    }
}
