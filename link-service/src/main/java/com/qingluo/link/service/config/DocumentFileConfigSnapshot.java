package com.qingluo.link.service.config;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFileConfigSnapshot {

    private long maxSizeBytes;
    private Set<String> allowedSuffixes = new LinkedHashSet<>();
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
