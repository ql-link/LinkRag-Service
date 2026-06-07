package com.qingluo.link.core.trace;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 链路追踪入口过滤器：为每个 HTTP 请求建立 traceId 并写入 MDC，全链路日志可串联。
 *
 * <p>优先复用上游传入的 {@code X-Trace-Id}（便于跨服务串联，如网关/前端/Python 端透传），
 * 缺失时新建。traceId 同时回写响应头，方便前端与排查时定位。请求结束在 finally 清理 MDC，
 * 避免 Tomcat 线程复用导致 traceId 串到下一个请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceContext.newTraceId();
        }
        TraceContext.put(traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
