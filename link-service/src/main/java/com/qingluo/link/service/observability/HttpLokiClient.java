package com.qingluo.link.service.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 基于 Loki HTTP API 的查询客户端。
 */
@Component
public class HttpLokiClient implements LokiClient {

    private final LokiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpLokiClient(LokiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout())
            .build();
    }

    @Override
    public String queryRange(String query, Instant start, Instant end, int limit) throws IOException, InterruptedException {
        String url = baseUrl() + "/loki/api/v1/query_range"
            + "?query=" + encode(query)
            + "&start=" + toNanoseconds(start)
            + "&end=" + toNanoseconds(end)
            + "&limit=" + limit
            + "&direction=BACKWARD";
        return sendGet(url);
    }

    @Override
    public List<String> labelValues(String label) throws IOException, InterruptedException {
        String body = sendGet(baseUrl() + "/loki/api/v1/label/" + encodePath(label) + "/values");
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        List<String> values = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private String sendGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(properties.getRequestTimeout())
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Loki request failed, status=" + response.statusCode());
        }
        return response.body();
    }

    private String baseUrl() {
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.isEmpty()) {
            return "http://localhost:3100";
        }
        return baseUrl;
    }

    private String toNanoseconds(Instant instant) {
        return String.valueOf(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }
}
