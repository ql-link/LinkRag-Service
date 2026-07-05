package com.qingluo.link.service.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LokiLogParserTest {

    private final LokiLogParser parser = new LokiLogParser(new ObjectMapper());

    @Test
    @DisplayName("解析 Java 顶层 JSON Lines 日志")
    void Should_ParseJavaJsonLine_When_LineHasTopLevelFields() {
        String line = """
            {"time":"2026-07-03T10:00:00Z","level":"ERROR","service":"tolink-service","host":"java-1","pid":"123","trace_id":"trace-java","logger_name":"com.qingluo.Test","message":"request failed token=abc","exception":"api_key=sk-test"}
            """;

        LogEntryDTO dto = parser.parseLine(line, Map.of("service", "tolink-service", "level", "ERROR"),
            "1783063200000000000");

        assertThat(dto.getTime()).isEqualTo("2026-07-03T10:00:00Z");
        assertThat(dto.getLevel()).isEqualTo("ERROR");
        assertThat(dto.getService()).isEqualTo("tolink-service");
        assertThat(dto.getTraceId()).isEqualTo("trace-java");
        assertThat(dto.getLoggerName()).isEqualTo("com.qingluo.Test");
        assertThat(dto.getMessage()).isEqualTo("request failed token=******");
        assertThat(dto.getException()).isEqualTo("api_key=******");
    }

    @Test
    @DisplayName("解析 Python Loguru serialize=True 日志")
    void Should_ParsePythonLoguruLine_When_LineHasRecordExtra() {
        String line = """
            {
              "text":"2026-07-03 10:00:00 ERROR rag failed",
              "record":{
                "message":"rag failed",
                "level":{"name":"ERROR"},
                "time":{"timestamp":1783063200.0,"repr":"2026-07-03T10:00:00+08:00"},
                "extra":{"trace_id":"trace-py","service":"tolink-rag","host":"rag-1","pid":4321,"logger_name":"rag.worker"},
                "process":{"id":4321},
                "name":"rag",
                "exception":{"type":"ValueError","value":"bad"}
              }
            }
            """;

        LogEntryDTO dto = parser.parseLine(line, Map.of("service", "tolink-rag", "level", "ERROR"),
            "1783063200000000000");

        assertThat(dto.getTime()).isEqualTo("2026-07-03T10:00:00+08:00");
        assertThat(dto.getLevel()).isEqualTo("ERROR");
        assertThat(dto.getService()).isEqualTo("tolink-rag");
        assertThat(dto.getHost()).isEqualTo("rag-1");
        assertThat(dto.getPid()).isEqualTo("4321");
        assertThat(dto.getTraceId()).isEqualTo("trace-py");
        assertThat(dto.getLoggerName()).isEqualTo("rag.worker");
        assertThat(dto.getMessage()).isEqualTo("rag failed");
        assertThat(dto.getException()).contains("ValueError");
    }

    @Test
    @DisplayName("非 JSON 原始日志不丢弃，message/raw 保留原始内容")
    void Should_KeepRawLine_When_LineIsNotJson() {
        LogEntryDTO dto = parser.parseLine("plain log token=secret", Map.of(
            "service", "tolink-service",
            "level", "WARN",
            "host", "java-1"), "1783063200000000000");

        assertThat(dto.getTime()).isNotBlank();
        assertThat(dto.getLevel()).isEqualTo("WARN");
        assertThat(dto.getService()).isEqualTo("tolink-service");
        assertThat(dto.getMessage()).isEqualTo("plain log token=******");
        assertThat(dto.getRaw()).isEqualTo("plain log token=******");
    }
}
