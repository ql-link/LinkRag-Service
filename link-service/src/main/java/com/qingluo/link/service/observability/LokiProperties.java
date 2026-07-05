package com.qingluo.link.service.observability;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Loki 查询代理配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "observability.loki")
public class LokiProperties {

    /**
     * Loki 内网地址，不应直接暴露公网。
     */
    private String baseUrl = "http://localhost:3100";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration requestTimeout = Duration.ofSeconds(8);

    /**
     * 未传 start_time / end_time 时默认查询最近 24 小时。
     */
    private Duration defaultLookback = Duration.ofHours(24);

    private int maxPageSize = 200;

    private int maxFetchLimit = 1000;
}
