package com.qingluo.link.service.llm.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * models.dev 外部模型目录适配器。
 */
@Service
@RequiredArgsConstructor
public class ModelsDevCatalogClient implements ExternalModelCatalogClient {

    public static final String SOURCE = "MODELS_DEV";
    private static final URI API_URI = URI.create("https://models.dev/api.json");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Map<String, String> PROVIDER_KEY_OVERRIDES = Map.of(
            "claude", "anthropic",
            "gemini", "google",
            "moonshot", "moonshotai",
            "volcengine", "volcengine",
            "aliyun", "alibaba-cn"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<ExternalModelCatalogEntry> listModels(SystemProvider provider) {
        JsonNode root = fetchCatalog();
        String providerKey = toModelsDevProviderKey(provider.getProviderType());
        JsonNode providerNode = root.get(providerKey);
        if (providerNode == null || providerNode.get("models") == null || !providerNode.get("models").isObject()) {
            throw new BusinessException(ErrorCode.MODEL_SYNC_SOURCE_UNSUPPORTED,
                    "models.dev 未收录该厂商：" + provider.getProviderType());
        }
        return parseModels(providerNode.get("models"));
    }

    private JsonNode fetchCatalog() {
        HttpRequest request = HttpRequest.newBuilder(API_URI)
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(50001, "models.dev 返回异常状态：" + response.statusCode(), 500);
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new BusinessException(50001, "读取 models.dev 模型目录失败：" + ex.getMessage(), 500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(50001, "读取 models.dev 模型目录被中断", 500);
        }
    }

    private List<ExternalModelCatalogEntry> parseModels(JsonNode modelsNode) {
        List<ExternalModelCatalogEntry> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = modelsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode modelNode = field.getValue();
            String modelName = textOrDefault(modelNode.get("id"), field.getKey());
            List<String> input = stringArray(modelNode.path("modalities").path("input"));
            List<String> output = stringArray(modelNode.path("modalities").path("output"));
            List<String> capabilities = inferCapabilities(modelName, textOrDefault(modelNode.get("name"), modelName),
                    input, output);
            if (capabilities.isEmpty()) {
                continue;
            }
            ExternalModelCatalogEntry entry = new ExternalModelCatalogEntry();
            entry.setExternalModelId(field.getKey());
            entry.setModelName(modelName);
            entry.setDisplayName(textOrDefault(modelNode.get("name"), modelName));
            entry.setCapabilities(capabilities);
            entry.setInputModalitiesJson(writeJson(input));
            entry.setOutputModalitiesJson(writeJson(output));
            entry.setContextWindow(intOrNull(modelNode.path("limit").path("context")));
            entry.setMaxOutputTokens(intOrNull(modelNode.path("limit").path("output")));
            entry.setReleaseDate(dateOrNull(modelNode.get("release_date")));
            entry.setRawMetadataJson(writeJson(modelNode));
            result.add(entry);
        }
        return result;
    }

    private String toModelsDevProviderKey(String providerType) {
        return PROVIDER_KEY_OVERRIDES.getOrDefault(providerType, providerType);
    }

    private List<String> inferCapabilities(String modelId, String displayName, List<String> input, List<String> output) {
        String joinedName = (modelId + " " + displayName).toLowerCase(Locale.ROOT);
        Set<String> caps = new LinkedHashSet<>();
        if (containsAny(joinedName, "rerank", "reranker")) {
            caps.add("RERANK");
            return List.copyOf(caps);
        }
        if (containsAny(joinedName, "embed", "embedding") || output.contains("embedding")) {
            caps.add("EMBEDDING");
            return List.copyOf(caps);
        }
        if (containsAny(joinedName, "whisper", "asr", "transcribe", "transcription", "speech-to-text")) {
            caps.add("ASR");
            return List.copyOf(caps);
        }
        if (output.contains("text") && input.contains("text")) {
            caps.add("CHAT");
        }
        if (output.contains("text") && (input.contains("image") || input.contains("video") || input.contains("pdf"))) {
            caps.add("VISION");
        }
        return List.copyOf(caps);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private String textOrDefault(JsonNode node, String fallback) {
        return node != null && node.isTextual() && StringUtils.hasText(node.asText()) ? node.asText() : fallback;
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || !node.canConvertToInt()) {
            return null;
        }
        return node.asInt();
    }

    private LocalDate dateOrNull(JsonNode node) {
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new BusinessException(50001, "序列化外部模型元数据失败：" + ex.getMessage(), 500);
        }
    }
}
