package com.qingluo.link.service.config;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFileRuntimeConfig {

    private long maxSizeBytes;
    private Set<String> allowedSuffixes = new LinkedHashSet<>();
}
