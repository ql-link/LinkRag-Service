package com.qingluo.link.core.web;

import com.qingluo.link.core.util.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 统一请求访问日志：一处覆盖全部 HTTP 端点，记录 方法 / 路径 / 状态码 / 耗时 / 用户 / 客户端 IP。
 *
 * <p>排在 {@code TraceIdFilter} 之后（依赖其已写入 MDC 的 traceId）。在 {@code finally} 中落日志，
 * 无论正常返回还是异常（GlobalExceptionHandler 处理后状态码已确定）都记录一行。userId 尽力获取，
 * 未登录或读取异常记为 {@code -}。专用 logger 名 {@code ACCESS}，便于单独调级或拆 appender。</p>
 *
 * <p>异步请求（SSE）由 {@link OncePerRequestFilter} 默认只在初始派发计时，记录的是建流耗时而非整段
 * 流持续时间，避免出现超长 cost。静态资源与接口文档路径跳过，降低噪声。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("ACCESS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String query = request.getQueryString();
            log.info("{} {}{} status={} cost={}ms userId={} ip={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    query == null ? "" : "?" + query,
                    response.getStatus(),
                    cost,
                    currentUserIdOrDash(),
                    clientIp(request));
        }
    }

    /** 尽力获取当前登录用户 ID；未登录或读取异常返回 "-"，绝不影响主流程。 */
    private String currentUserIdOrDash() {
        try {
            Long userId = AuthContext.getCurrentUserId();
            return userId == null ? "-" : userId.toString();
        } catch (Exception e) {
            return "-";
        }
    }

    /** 取客户端 IP：优先 X-Forwarded-For 首段（经网关/反代时），否则 remoteAddr。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars")
                || uri.startsWith("/doc.html")
                || uri.startsWith("/favicon")
                || uri.equals("/error");
    }
}
