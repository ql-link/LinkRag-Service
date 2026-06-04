package com.qingluo.link.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 模型列表拉取配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tolink.llm.model-fetch")
public class LlmModelFetchProperties {

    private long connectTimeoutMs = 3000;
    private long readTimeoutMs = 10000;
    private long callTimeoutMs = 15000;
    private boolean allowHttp = false;
    private boolean blockPrivateAddress = true;
    private int maxModels = 500;
}
