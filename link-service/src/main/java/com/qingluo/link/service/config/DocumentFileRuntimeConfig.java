package com.qingluo.link.service.config;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DocumentFileRuntimeConfig {

    private long maxSizeBytes;
    private Set<String> allowedSuffixes = new LinkedHashSet<>();
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public DocumentFileRuntimeConfig(long maxSizeBytes, Set<String> allowedSuffixes) {
        this(maxSizeBytes, allowedSuffixes, null, null);
    }

    public DocumentFileRuntimeConfig(long maxSizeBytes, Set<String> allowedSuffixes,
                                     Long updatedBy, LocalDateTime updatedAt) {
        this.maxSizeBytes = maxSizeBytes;
        this.allowedSuffixes = allowedSuffixes;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }
}
