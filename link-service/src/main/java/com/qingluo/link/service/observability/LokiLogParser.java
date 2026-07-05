package com.qingluo.link.service.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 把 Loki 返回的 Java JSON Lines / Python Loguru serialize=True / 原始文本统一成前端字段。
 */
@Component
public class LokiLogParser {

    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
        "(?i)(\\\"?(?:api[_-]?key|access[_-]?key(?:[_-]?id)?|secret[_-]?key|authorization|token|password|secret)\\\"?\\s*[:=]\\s*\\\"?)([^\\s,\\\"'}]+)");

    private final ObjectMapper objectMapper;

    public LokiLogParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<LogEntryDTO> parseQueryRange(String responseBody) {
        List<ParsedLogEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("data").path("result");
            if (!results.isArray()) {
                return List.of();
            }
            for (JsonNode result : results) {
                Map<String, String> stream = parseStream(result.path("stream"));
                JsonNode values = result.path("values");
                if (!values.isArray()) {
                    continue;
                }
                for (JsonNode value : values) {
                    if (!value.isArray() || value.size() < 2) {
                        continue;
                    }
                    String timestamp = value.get(0).asText();
                    String line = value.get(1).asText();
                    LogEntryDTO dto = parseLine(line, stream, timestamp);
                    entries.add(new ParsedLogEntry(dto, parseTimestampNanos(timestamp)));
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        entries.sort(Comparator.comparingLong(ParsedLogEntry::getTimestampNanos).reversed());
        return entries.stream().map(ParsedLogEntry::getEntry).toList();
    }

    public LogEntryDTO parseLine(String line, Map<String, String> stream, String timestampNanos) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (root.has("record")) {
                return parsePythonLoguru(root, stream, timestampNanos);
            }
            return parseJavaJson(root, stream, timestampNanos);
        } catch (Exception e) {
            return rawEntry(line, stream, timestampNanos);
        }
    }

    private LogEntryDTO parseJavaJson(JsonNode root, Map<String, String> stream, String timestampNanos) {
        LogEntryDTO dto = baseEntry(stream, timestampNanos);
        dto.setTime(firstText(root, "time", "@timestamp", "timestamp"));
        dto.setLevel(firstText(root, "level"));
        dto.setService(firstNonBlank(firstText(root, "service"), stream.get("service")));
        dto.setHost(firstNonBlank(firstText(root, "host"), stream.get("host")));
        dto.setPid(firstText(root, "pid"));
        dto.setTraceId(firstText(root, "trace_id", "traceId"));
        dto.setLoggerName(firstText(root, "logger_name", "loggerName", "logger"));
        dto.setMessage(redact(firstText(root, "message")));
        dto.setException(redact(firstText(root, "exception", "stack_trace", "stackTrace")));
        normalizeAccessAuditLevel(dto);
        if (dto.getTime() == null) {
            dto.setTime(toIsoTime(timestampNanos));
        }
        return dto;
    }

    private LogEntryDTO parsePythonLoguru(JsonNode root, Map<String, String> stream, String timestampNanos) {
        JsonNode record = root.path("record");
        JsonNode extra = record.path("extra");
        LogEntryDTO dto = baseEntry(stream, timestampNanos);
        dto.setTime(firstNonBlank(
            firstText(record.path("time"), "repr"),
            timestampToIso(record.path("time").path("timestamp")),
            toIsoTime(timestampNanos)));
        dto.setLevel(firstText(record.path("level"), "name"));
        dto.setService(firstNonBlank(firstText(extra, "service"), stream.get("service")));
        dto.setHost(firstNonBlank(firstText(extra, "host"), stream.get("host")));
        dto.setPid(firstNonBlank(firstText(extra, "pid"), firstText(record.path("process"), "id")));
        dto.setTraceId(firstText(extra, "trace_id", "traceId"));
        dto.setLoggerName(firstNonBlank(firstText(extra, "logger_name", "loggerName"), firstText(record, "name")));
        dto.setMessage(redact(firstText(record, "message")));
        dto.setException(redact(firstText(record, "exception")));
        normalizeAccessAuditLevel(dto);
        return dto;
    }

    private LogEntryDTO rawEntry(String line, Map<String, String> stream, String timestampNanos) {
        LogEntryDTO dto = baseEntry(stream, timestampNanos);
        String redactedLine = redact(line);
        dto.setMessage(redactedLine);
        dto.setRaw(redactedLine);
        return dto;
    }

    private LogEntryDTO baseEntry(Map<String, String> stream, String timestampNanos) {
        LogEntryDTO dto = new LogEntryDTO();
        dto.setTime(toIsoTime(timestampNanos));
        dto.setLevel(stream.get("level"));
        dto.setService(stream.get("service"));
        dto.setHost(stream.get("host"));
        return dto;
    }

    private Map<String, String> parseStream(JsonNode streamNode) {
        Map<String, String> stream = new HashMap<>();
        if (streamNode != null && streamNode.isObject()) {
            streamNode.fields().forEachRemaining(entry -> stream.put(entry.getKey(), entry.getValue().asText()));
        }
        return stream;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String text(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void normalizeAccessAuditLevel(LogEntryDTO dto) {
        String loggerName = dto.getLoggerName();
        if ("ACCESS".equals(loggerName) || "AUDIT".equals(loggerName)) {
            dto.setLevel(loggerName);
        }
    }

    private String timestampToIso(JsonNode timestampNode) {
        if (timestampNode == null || !timestampNode.isNumber()) {
            return null;
        }
        long millis = (long) (timestampNode.asDouble() * 1000);
        return Instant.ofEpochMilli(millis).toString();
    }

    private long parseTimestampNanos(String timestampNanos) {
        try {
            return Long.parseLong(timestampNanos);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String toIsoTime(String timestampNanos) {
        long nanos = parseTimestampNanos(timestampNanos);
        if (nanos <= 0) {
            return null;
        }
        long seconds = nanos / 1_000_000_000L;
        int nanoAdjustment = (int) (nanos % 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanoAdjustment).toString();
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        return SENSITIVE_VALUE.matcher(value).replaceAll("$1******");
    }

    private static class ParsedLogEntry {
        private final LogEntryDTO entry;
        private final long timestampNanos;

        private ParsedLogEntry(LogEntryDTO entry, long timestampNanos) {
            this.entry = entry;
            this.timestampNanos = timestampNanos;
        }

        private LogEntryDTO getEntry() {
            return entry;
        }

        private long getTimestampNanos() {
            return timestampNanos;
        }
    }
}
