package com.qingluo.link.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceIdFilter} 单测：覆盖入站合法头复用、非法/缺失时新建（防日志注入）、响应头回写与请求后 MDC 清理。
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_reuse_valid_inbound_trace_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "valid-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = doFilterCapturingTraceId(request, response);

        assertEquals("valid-trace-123", seen);
        assertEquals("valid-trace-123", response.getHeader(TraceContext.TRACE_ID_HEADER));
    }

    @Test
    void should_generate_new_id_when_header_missing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = doFilterCapturingTraceId(request, response);

        assertNotNull(seen);
        assertTrue(TraceContext.isValid(seen));
        assertEquals(seen, response.getHeader(TraceContext.TRACE_ID_HEADER));
    }

    @Test
    void should_reject_injection_attempt_and_generate_new_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 含换行的恶意 traceId：日志注入向量，必须被拒并改为新建
        request.addHeader(TraceContext.TRACE_ID_HEADER, "abc\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = doFilterCapturingTraceId(request, response);

        assertNotNull(seen);
        assertTrue(TraceContext.isValid(seen));
        assertEquals(seen, response.getHeader(TraceContext.TRACE_ID_HEADER));
        // 关键：未把恶意值写入 MDC/日志
        org.junit.jupiter.api.Assertions.assertNotEquals("abc\nFAKE LOG LINE", seen);
    }

    @Test
    void should_clear_mdc_after_request() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
    }

    /** 在过滤链内部捕获当时 MDC 中的 traceId，验证请求处理期间 traceId 已就绪。 */
    private String doFilterCapturingTraceId(MockHttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                captured.set(MDC.get(TraceContext.TRACE_ID_KEY));
            }
        };
        filter.doFilter(request, response, chain);
        return captured.get();
    }
}
