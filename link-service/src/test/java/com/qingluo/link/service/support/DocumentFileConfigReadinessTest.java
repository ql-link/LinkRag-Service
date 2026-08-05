package com.qingluo.link.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentFileConfigReadinessTest {

    @Test
    void fingerprintUsesStableFieldOrderAndMigratesFormerDefaultSuffixes() {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setAllowedSuffixes(
            new LinkedHashSet<>(List.of("txt", "pdf", "md", "docx", "markdown")));
        DocumentFileConfigReadiness readiness =
            new DocumentFileConfigReadiness(null, properties, new ObjectMapper());

        assertEquals(
            "342e32f6f6b653dead9989bf5e49535b3498e04a747b468b5985fde35ab54774",
            readiness.fingerprint());
    }
}
