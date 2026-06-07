package com.qingluo.link.core.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccessLogFilter} 单测：覆盖正常放行、异常仍放行（finally 落日志不吞异常）、文档/静态路径跳过。
 */
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    @Test
    void should_pass_through_chain_for_normal_request() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/datasets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 链已被调用（请求被放行）
        org.junit.jupiter.api.Assertions.assertNotNull(chain.getRequest());
    }

    @Test
    void should_not_swallow_downstream_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain throwing = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                throw new RuntimeException("downstream boom");
            }
        };

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> filter.doFilter(request, response, throwing));
    }

    @Test
    void shouldNotFilter_should_skip_docs_and_static_paths() {
        assertTrue(invokeShouldNotFilter("/swagger-ui/index.html"));
        assertTrue(invokeShouldNotFilter("/v3/api-docs"));
        assertTrue(invokeShouldNotFilter("/doc.html"));
        assertTrue(invokeShouldNotFilter("/webjars/x.js"));
        assertTrue(invokeShouldNotFilter("/favicon.ico"));
        assertTrue(invokeShouldNotFilter("/error"));
    }

    @Test
    void shouldNotFilter_should_keep_business_apis() {
        assertFalse(invokeShouldNotFilter("/api/v1/llm/configs"));
        assertFalse(invokeShouldNotFilter("/api/v1/auth/login"));
    }

    private boolean invokeShouldNotFilter(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        try {
            java.lang.reflect.Method m = AccessLogFilter.class.getDeclaredMethod(
                    "shouldNotFilter", javax.servlet.http.HttpServletRequest.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, request);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
