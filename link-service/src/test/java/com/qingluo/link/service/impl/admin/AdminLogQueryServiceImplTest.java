package com.qingluo.link.service.impl.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import com.qingluo.link.model.dto.response.LogLabelsDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.observability.LokiClient;
import com.qingluo.link.service.observability.LokiLogParser;
import com.qingluo.link.service.observability.LokiLogQueryBuilder;
import com.qingluo.link.service.observability.LokiProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminLogQueryServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LokiProperties properties = new LokiProperties();
    private final FakeLokiClient lokiClient = new FakeLokiClient();
    private final AdminLogQueryServiceImpl service = new AdminLogQueryServiceImpl(
        lokiClient,
        new LokiLogQueryBuilder(properties),
        new LokiLogParser(objectMapper));

    @Test
    @DisplayName("查询日志时生成安全 LogQL 并解析 Loki 响应")
    void Should_QueryLokiWithSafeLogQL_When_QueryLogs() throws Exception {
        String line = """
            {"time":"2026-07-03T10:00:00Z","level":"ERROR","service":"tolink-service","host":"java-1","pid":"123","trace_id":"trace-1","logger_name":"com.qingluo.Test","message":"boom","exception":""}
            """;
        lokiClient.queryRangeResponse = lokiResponse(line);

        PageResult<LogEntryDTO> result = service.queryLogs(
            "tolink-service",
            "ERROR",
            "trace-1",
            "boom \"x\"",
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50);

        assertThat(lokiClient.lastQuery)
            .isEqualTo("{service=\"tolink-service\", level=\"ERROR\"} |= \"trace-1\" |= \"boom \\\"x\\\"\"");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getTraceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("Loki 查询失败时返回日志服务不可用")
    void Should_ThrowBusinessException_When_LokiQueryFails() {
        lokiClient.queryFailure = true;

        assertThatThrownBy(() -> service.queryLogs(
            null, null, null, null,
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50))
            .isInstanceOf(BusinessException.class)
            .hasMessage("日志服务暂不可用");
    }

    @Test
    @DisplayName("labels 接口优先返回 Loki service labels")
    void Should_ReturnLokiLabels_When_LokiHasServices() {
        lokiClient.labels = List.of("tolink-rag", "bad service", "tolink-service", "tolink-rag");

        LogLabelsDTO labels = service.listLabels();

        assertThat(labels.getServices()).containsExactly("tolink-rag", "tolink-service");
        assertThat(labels.getLevels()).contains("INFO", "ERROR", "ACCESS", "AUDIT");
    }

    @Test
    @DisplayName("labels 查询 Loki 异常时返回兜底服务")
    void Should_ReturnFallbackLabels_When_LokiLabelsFail() {
        lokiClient.labelFailure = true;

        LogLabelsDTO labels = service.listLabels();

        assertThat(labels.getServices()).containsExactly("tolink-service", "tolink-rag");
        assertThat(labels.getLevels()).contains("INFO", "ERROR", "ACCESS", "AUDIT");
    }

    private String lokiResponse(String line) throws Exception {
        return """
            {"status":"success","data":{"result":[{"stream":{"service":"tolink-service","level":"ERROR","host":"java-1"},"values":[["1783063200000000000",%s]]}]}}
            """.formatted(objectMapper.writeValueAsString(line));
    }

    private static class FakeLokiClient implements LokiClient {
        private String lastQuery;
        private String queryRangeResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";
        private boolean queryFailure;
        private List<String> labels = List.of();
        private boolean labelFailure;

        @Override
        public String queryRange(String query, Instant start, Instant end, int limit) throws IOException {
            this.lastQuery = query;
            if (queryFailure) {
                throw new IOException("loki down");
            }
            return queryRangeResponse;
        }

        @Override
        public List<String> labelValues(String label) throws IOException {
            if (labelFailure) {
                throw new IOException("loki down");
            }
            return labels;
        }
    }
}
