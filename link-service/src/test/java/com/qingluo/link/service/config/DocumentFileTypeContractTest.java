package com.qingluo.link.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentFileTypeContractTest {

    @Test
    void supportedSuffixes_matchPythonParserContract() {
        assertThat(DocumentFileTypeContract.supportedSuffixes())
            .containsExactly("md", "markdown", "pdf", "docx", "html", "htm");
    }

    @Test
    void resolveDeploymentSuffixes_migratesFormerShippedDefault() {
        LinkedHashSet<String> formerDefault =
            new LinkedHashSet<>(List.of("md", "markdown", "pdf", "docx", "txt"));

        assertThat(DocumentFileTypeContract.resolveDeploymentSuffixes(formerDefault))
            .containsExactly("md", "markdown", "pdf", "docx", "html", "htm");
    }

    @Test
    void resolveDeploymentSuffixes_rejectsUnsupportedCustomSuffix() {
        assertThatThrownBy(() -> DocumentFileTypeContract.resolveDeploymentSuffixes(
                new LinkedHashSet<>(List.of("pdf", "txt"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("allowed-suffixes");
    }
}
