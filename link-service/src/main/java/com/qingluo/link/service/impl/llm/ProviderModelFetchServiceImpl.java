package com.qingluo.link.service.impl.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.FetchProviderModelsRequest;
import com.qingluo.link.model.dto.response.ProviderModelListDTO;
import com.qingluo.link.model.dto.response.ProviderModelOptionDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.LlmModelFetchProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 基于系统 provider schema 临时拉取上游模型列表。
 */
@Slf4j
@Service
public class ProviderModelFetchServiceImpl implements ProviderModelFetchService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final LlmModelFetchProperties properties;
    private final ApiBaseUrlGuard apiBaseUrlGuard;

    public ProviderModelFetchServiceImpl(@Qualifier("llmModelFetchOkHttpClient") OkHttpClient okHttpClient,
                                         ObjectMapper objectMapper,
                                         LlmModelFetchProperties properties,
                                         ApiBaseUrlGuard apiBaseUrlGuard) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiBaseUrlGuard = apiBaseUrlGuard;
    }

    @Override
    public ProviderModelListDTO fetchModels(SystemProvider provider, FetchProviderModelsRequest request) {
        ModelFetchSpec spec = parseSpec(provider.getConfigSchema());
        if (!spec.isEnabled()) {
            throw new BusinessException(ErrorCode.MODEL_FETCH_UNSUPPORTED);
        }
        URI target = buildTargetUri(provider, request, spec);
        Request httpRequest = buildHttpRequest(target, request.getApiKey(), spec);
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Fetch provider models failed, providerType={}, status={}",
                        provider.getProviderType(), response.code());
                throw new BusinessException(ErrorCode.MODEL_FETCH_FAILED);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new BusinessException(ErrorCode.MODEL_FETCH_FAILED);
            }
            JsonNode root = objectMapper.readTree(body.string());
            return ProviderModelListDTO.success(extractModels(root, spec));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Fetch provider models failed, providerType={}, error={}",
                    provider.getProviderType(), ex.getMessage());
            throw new BusinessException(ErrorCode.MODEL_FETCH_FAILED);
        }
    }

    private ModelFetchSpec parseSpec(String configSchema) {
        if (!StringUtils.hasText(configSchema)) {
            throw new BusinessException(ErrorCode.MODEL_FETCH_UNSUPPORTED);
        }
        try {
            JsonNode root = objectMapper.readTree(configSchema);
            JsonNode modelFetch = root.path("modelFetch");
            if (modelFetch.isMissingNode() || modelFetch.isNull()) {
                throw new BusinessException(ErrorCode.MODEL_FETCH_UNSUPPORTED);
            }
            return objectMapper.convertValue(modelFetch, ModelFetchSpec.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "厂商模型拉取配置不合法");
        }
    }

    private URI buildTargetUri(SystemProvider provider, FetchProviderModelsRequest request, ModelFetchSpec spec) {
        if (!"GET".equalsIgnoreCase(spec.getMethod())) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "模型拉取首版仅支持 GET");
        }
        if (!StringUtils.hasText(spec.getUrlTemplate()) || spec.getUrlTemplate().contains("{apiKey}")) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "模型拉取 URL 配置不合法");
        }
        String baseUrl = StringUtils.hasText(request.getCustomApiBaseUrl())
                ? request.getCustomApiBaseUrl()
                : provider.getApiBaseUrl();
        apiBaseUrlGuard.validate(baseUrl);
        String url = spec.getUrlTemplate().replace("{baseUrl}", trimRightSlash(baseUrl));
        return apiBaseUrlGuard.validate(url);
    }

    private Request buildHttpRequest(URI uri, String apiKey, ModelFetchSpec spec) {
        Request.Builder builder = new Request.Builder().url(uri.toString()).get();
        if (spec.getHeaders() != null) {
            spec.getHeaders().forEach(builder::header);
        }
        ModelFetchSpec.Auth auth = spec.getAuth();
        String header = StringUtils.hasText(auth.getHeaderName()) ? auth.getHeaderName() : auth.getHeader();
        if (!StringUtils.hasText(header)) {
            header = "Authorization";
        }
        if ("header".equalsIgnoreCase(auth.getType()) || "api_key_header".equalsIgnoreCase(auth.getType())) {
            builder.header(header, apiKey);
        } else {
            String prefix = auth.getPrefix() != null ? auth.getPrefix() : "Bearer ";
            builder.header(header, prefix + apiKey);
        }
        return builder.build();
    }

    private List<ProviderModelOptionDTO> extractModels(JsonNode root, ModelFetchSpec spec) {
        JsonNode items = readPath(root, spec.getResponse().getItemsPath());
        if (!items.isArray()) {
            throw new BusinessException(ErrorCode.MODEL_FETCH_FAILED);
        }
        List<ProviderModelOptionDTO> models = new ArrayList<>();
        Iterator<JsonNode> iterator = items.elements();
        while (iterator.hasNext() && models.size() < properties.getMaxModels()) {
            JsonNode item = iterator.next();
            String id = readText(item, spec.getResponse().getIdPath());
            if (!StringUtils.hasText(id)) {
                continue;
            }
            String displayName = readText(item, spec.getResponse().getDisplayNamePath());
            String ownedBy = readText(item, spec.getResponse().getOwnedByPath());
            models.add(new ProviderModelOptionDTO(id, StringUtils.hasText(displayName) ? displayName : id, ownedBy));
        }
        return models;
    }

    private JsonNode readPath(JsonNode root, String path) {
        if (!StringUtils.hasText(path)) {
            return root;
        }
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private String readText(JsonNode root, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        JsonNode node = readPath(root, path);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String trimRightSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
