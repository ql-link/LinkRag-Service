package com.qingluo.link.service.config;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * LLM 模型列表拉取组件装配。
 */
@Configuration
@RequiredArgsConstructor
public class LlmModelFetchConfig {

    private final LlmModelFetchProperties properties;

    @Bean("llmModelFetchOkHttpClient")
    public OkHttpClient llmModelFetchOkHttpClient() {
        return new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .callTimeout(properties.getCallTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }
}
