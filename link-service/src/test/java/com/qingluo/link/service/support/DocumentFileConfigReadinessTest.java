package com.qingluo.link.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentFileConfigReadinessTest {

    @Test
    void fingerprintUsesStableFieldAndSuffixOrder() {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setAllowedSuffixes(
            new LinkedHashSet<>(List.of("txt", "pdf", "md", "docx", "markdown")));
        DocumentFileConfigReadiness readiness =
            new DocumentFileConfigReadiness(null, properties, new ObjectMapper());

        assertEquals(
            "941d534cd0926b55d21c652204e3bb11da05294228f0c9cc54cdd8cb262b6deb",
            readiness.fingerprint());
    }
}
