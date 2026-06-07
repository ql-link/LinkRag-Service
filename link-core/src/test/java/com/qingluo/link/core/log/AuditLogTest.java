package com.qingluo.link.core.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditLog} 单测：验证写到 AUDIT logger、带 action= 前缀、占位参数被正确渲染。
 */
class AuditLogTest {

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void event_should_write_to_audit_logger_with_action_and_rendered_detail() {
        AuditLog.event("LOGIN_SUCCESS", "userId={}, account={}", 10001L, "alice");

        assertEquals(1, appender.list.size());
        ILoggingEvent e = appender.list.get(0);
        assertEquals(Level.INFO, e.getLevel());
        String msg = e.getFormattedMessage();
        assertTrue(msg.contains("action=LOGIN_SUCCESS"), msg);
        assertTrue(msg.contains("userId=10001"), msg);
        assertTrue(msg.contains("account=alice"), msg);
    }
}
