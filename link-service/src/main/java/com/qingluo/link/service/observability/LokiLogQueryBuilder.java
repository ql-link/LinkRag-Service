package com.qingluo.link.service.observability;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.observability.trace.TraceContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 安全构造 LogQL，避免把用户输入直接拼进 Loki 查询语句。
 */
@Component
public class LokiLogQueryBuilder {

    public static final List<String> SUPPORTED_LEVELS = List.of(
        "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "ACCESS", "AUDIT");

    private static final Set<String> LOGGER_NAME_LEVELS = Set.of("ACCESS", "AUDIT");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\r\\n\\t\\u0000-\\u001F]");
    private static final int MAX_LEVEL_LENGTH = 16;
    private static final int MAX_TIME_LENGTH = 64;
    private static final int MAX_KEYWORD_LENGTH = 200;

    private final LokiProperties properties;

    public LokiLogQueryBuilder(LokiProperties properties) {
        this.properties = properties;
    }

    public LokiLogQuery build(LogQueryCriteria criteria) {
        int page = normalizePage(criteria.getPage());
        int pageSize = normalizePageSize(criteria.getPageSize());
        Instant end = parseIsoTime(criteria.getEndTime(), "end_time", Instant.now());
        Instant start = parseIsoTime(criteria.getStartTime(), "start_time",
            end.minus(properties.getDefaultLookback()));
        if (start.isAfter(end)) {
            throw badRequest("start_time 不能晚于 end_time");
        }

        String service = normalizeService(criteria.getService());
        String level = normalizeLevel(criteria.getLevel());
        String traceId = normalizeTraceId(criteria.getTraceId());
        String keyword = normalizeKeyword(criteria.getKeyword());

        StringBuilder selector = new StringBuilder("{");
        boolean hasLabel = false;
        if (service != null) {
            selector.append("service=\"").append(service).append("\"");
            hasLabel = true;
        }
        if (level != null && !LOGGER_NAME_LEVELS.contains(level)) {
            if (hasLabel) {
                selector.append(", ");
            }
            selector.append("level=\"").append(level).append("\"");
            hasLabel = true;
        }
        if (!hasLabel) {
            selector.append("service=~\".+\"");
        }
        selector.append("}");

        List<String> filters = new ArrayList<>();
        if (level != null && LOGGER_NAME_LEVELS.contains(level)) {
            filters.add("\"logger_name\":\"" + level + "\"");
        }
        if (traceId != null) {
            filters.add(traceId);
        }
        if (keyword != null) {
            filters.add(keyword);
        }

        StringBuilder query = new StringBuilder(selector);
        for (String filter : filters) {
            query.append(" |= \"").append(escapeLogqlString(filter)).append("\"");
        }

        int requestedLimit = page * pageSize;
        int limit = Math.min(requestedLimit, Math.max(pageSize, properties.getMaxFetchLimit()));
        return new LokiLogQuery(query.toString(), start, end, page, pageSize, limit);
    }

    private int normalizePage(int page) {
        if (page < 1) {
            return 1;
        }
        return Math.min(page, 1000);
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, Math.max(1, properties.getMaxPageSize()));
    }

    private String normalizeService(String value) {
        String service = trimToNull(value);
        if (service == null) {
            return null;
        }
        if (!SERVICE_PATTERN.matcher(service).matches()) {
            throw badRequest("service 仅支持字母、数字、下划线、连字符，长度 1~64");
        }
        return service;
    }

    private String normalizeLevel(String value) {
        String level = trimToNull(value);
        if (level == null) {
            return null;
        }
        if (level.length() > MAX_LEVEL_LENGTH) {
            throw badRequest("level 长度不能超过 " + MAX_LEVEL_LENGTH);
        }
        level = level.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_LEVELS.contains(level)) {
            throw badRequest("不支持的日志级别: " + value);
        }
        return level;
    }

    private String normalizeTraceId(String value) {
        String traceId = trimToNull(value);
        if (traceId == null) {
            return null;
        }
        if (!TraceContext.isValid(traceId)) {
            throw badRequest("trace_id 仅支持字母、数字、下划线、连字符，长度 1~64");
        }
        return traceId;
    }

    private String normalizeKeyword(String value) {
        String keyword = trimToNull(value);
        if (keyword == null) {
            return null;
        }
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw badRequest("keyword 长度不能超过 " + MAX_KEYWORD_LENGTH);
        }
        if (CONTROL_CHAR_PATTERN.matcher(keyword).find()) {
            throw badRequest("keyword 不能包含控制字符");
        }
        return keyword;
    }

    private Instant parseIsoTime(String value, String fieldName, Instant defaultValue) {
        String text = trimToNull(value);
        if (text == null) {
            return defaultValue;
        }
        if (text.length() > MAX_TIME_LENGTH) {
            throw badRequest(fieldName + " 长度不能超过 " + MAX_TIME_LENGTH);
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 兼容带 offset 的 ISO 时间，例如 2026-07-03T10:00:00+08:00。
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容不带时区的 ISO local datetime，按服务本地时区解释。
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw badRequest(fieldName + " 必须是 ISO 时间");
        }
    }

    private String escapeLogqlString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(400, message, 400);
    }
}
