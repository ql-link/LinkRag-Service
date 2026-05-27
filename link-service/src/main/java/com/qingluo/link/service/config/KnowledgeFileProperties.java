package com.qingluo.link.service.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tolink.knowledge-file")
public class KnowledgeFileProperties {

    private Set<String> allowedSuffixes = new LinkedHashSet<>(Set.of("md", "markdown", "pdf", "docx", "txt"));
    private long maxSizeBytes = 20L * 1024 * 1024;
    private String internalBaseUrl = "http://localhost:8080";
    private String serviceToken;
    private long sseTimeoutMs = 300_000L;

    public Set<String> getAllowedSuffixes() {
        return allowedSuffixes;
    }

    public void setAllowedSuffixes(Set<String> allowedSuffixes) {
        this.allowedSuffixes = allowedSuffixes;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public String getInternalBaseUrl() {
        return internalBaseUrl;
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
        this.internalBaseUrl = internalBaseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }
}
