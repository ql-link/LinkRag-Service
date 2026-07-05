package com.qingluo.link.service.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LokiLogQueryBuilderTest {

    private final LokiProperties properties = new LokiProperties();
    private final LokiLogQueryBuilder builder = new LokiLogQueryBuilder(properties);

    @Test
    @DisplayName("按 service/level/trace_id/keyword 生成安全 LogQL")
    void Should_BuildSafeLogQL_When_QueryContainsFilters() {
        LokiLogQuery query = builder.build(new LogQueryCriteria(
            "tolink-service",
            "ERROR",
            "trace-123",
            "timeout \"socket\"",
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50));

        assertThat(query.getQuery())
            .isEqualTo("{service=\"tolink-service\", level=\"ERROR\"} |= \"trace-123\" |= \"timeout \\\"socket\\\"\"");
        assertThat(query.getLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("ACCESS/AUDIT 使用 logger_name 文本过滤而不是 level label")
    void Should_FilterByLoggerName_When_LevelIsAccessOrAudit() {
        LokiLogQuery query = builder.build(new LogQueryCriteria(
            "tolink-service",
            "ACCESS",
            null,
            null,
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50));

        assertThat(query.getQuery())
            .isEqualTo("{service=\"tolink-service\"} |= \"\\\"logger_name\\\":\\\"ACCESS\\\"\"");
    }

    @Test
    @DisplayName("非法 trace_id 被拒绝，避免 LogQL 注入")
    void Should_RejectInvalidTraceId_When_TraceIdContainsUnsafeChars() {
        assertThatThrownBy(() -> builder.build(new LogQueryCriteria(
            "tolink-service",
            "ERROR",
            "bad\" |= \"x",
            null,
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("trace_id");
    }

    @Test
    @DisplayName("非法 service 被拒绝")
    void Should_RejectInvalidService_When_ServiceContainsUnsafeChars() {
        assertThatThrownBy(() -> builder.build(new LogQueryCriteria(
            "tolink-service\"",
            "ERROR",
            null,
            null,
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("service");
    }
}
