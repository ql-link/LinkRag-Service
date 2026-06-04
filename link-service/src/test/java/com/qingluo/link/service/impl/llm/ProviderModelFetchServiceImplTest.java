package com.qingluo.link.service.impl.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.FetchProviderModelsRequest;
import com.qingluo.link.model.dto.response.ProviderModelListDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.LlmModelFetchProperties;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderModelFetchServiceImplTest {

    @Test
    void Should_FetchModels_When_OpenAICompatibleSpecConfigured() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"data\":[{\"id\":\"gpt-4o\",\"owned_by\":\"openai\"}]}"));
            server.start();

            ProviderModelFetchServiceImpl service = buildService();
            FetchProviderModelsRequest request = new FetchProviderModelsRequest();
            request.setApiKey("sk-test");

            ProviderModelListDTO result = service.fetchModels(buildProvider(server.url("/v1").toString()), request);

            assertThat(result.getAllowManualInput()).isFalse();
            assertThat(result.getModels()).hasSize(1);
            assertThat(result.getModels().get(0).getId()).isEqualTo("gpt-4o");
            RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(recorded).isNotNull();
            assertThat(recorded.getPath()).isEqualTo("/v1/models");
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        }
    }

    @Test
    void Should_RejectSpec_When_ApiKeyAppearsInUrlTemplate() {
        ProviderModelFetchServiceImpl service = buildService();
        SystemProvider provider = buildProvider("https://api.example.com/v1");
        provider.setConfigSchema("{\"modelFetch\":{\"enabled\":true,\"method\":\"GET\","
                + "\"urlTemplate\":\"{baseUrl}/models?key={apiKey}\",\"auth\":{\"type\":\"bearer\"},"
                + "\"response\":{\"itemsPath\":\"data\",\"idPath\":\"id\"}}}");
        FetchProviderModelsRequest request = new FetchProviderModelsRequest();
        request.setApiKey("sk-test");

        assertThatThrownBy(() -> service.fetchModels(provider, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_CONFIG.getCode());
    }

    private ProviderModelFetchServiceImpl buildService() {
        LlmModelFetchProperties properties = new LlmModelFetchProperties();
        properties.setAllowHttp(true);
        properties.setBlockPrivateAddress(false);
        return new ProviderModelFetchServiceImpl(
                new OkHttpClient(),
                new ObjectMapper(),
                properties,
                new ApiBaseUrlGuard(properties));
    }

    private SystemProvider buildProvider(String baseUrl) {
        SystemProvider provider = new SystemProvider();
        provider.setProviderType("openai");
        provider.setProviderName("OpenAI");
        provider.setApiBaseUrl(baseUrl);
        provider.setConfigSchema("{\"modelFetch\":{\"enabled\":true,\"method\":\"GET\","
                + "\"urlTemplate\":\"{baseUrl}/models\",\"auth\":{\"type\":\"bearer\"},"
                + "\"response\":{\"itemsPath\":\"data\",\"idPath\":\"id\",\"ownedByPath\":\"owned_by\"}}}");
        return provider;
    }
}
