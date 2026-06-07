package com.qingluo.link.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TraceContext} 单测：覆盖 traceId 生成、合法性白名单（防日志注入）、MDC 读写与清理。
 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void newTraceId_should_be_nonblank_hex_without_dash() {
        String id = TraceContext.newTraceId();
        assertNotNull(id);
        assertEquals(32, id.length());
        assertFalse(id.contains("-"));
    }

    @Test
    void isValid_should_accept_normal_ids() {
        assertTrue(TraceContext.isValid(TraceContext.newTraceId()));
        assertTrue(TraceContext.isValid("abc-123_DEF"));
    }

    @Test
    void isValid_should_reject_null_blank_overlong_and_control_chars() {
        assertFalse(TraceContext.isValid(null));
        assertFalse(TraceContext.isValid(""));
        // 含换行：典型日志注入向量（CWE-117）
        assertFalse(TraceContext.isValid("abc\nFAKE LOG LINE"));
        assertFalse(TraceContext.isValid("has space"));
        // 超过 64 位
        assertFalse(TraceContext.isValid("a".repeat(65)));
    }

    @Test
    void put_and_clear_should_manage_mdc() {
        TraceContext.put("trace-1");
        assertEquals("trace-1", MDC.get(TraceContext.TRACE_ID_KEY));
        TraceContext.clear();
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    void startNew_should_put_and_return_same_value() {
        String id = TraceContext.startNew();
        assertNotNull(id);
        assertEquals(id, MDC.get(TraceContext.TRACE_ID_KEY));
    }
}
