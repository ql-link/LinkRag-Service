package com.qingluo.link.service.impl.document;

import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFileRuntimeConfigServiceImplTest {

    @Test
    @DisplayName("上传运行配置直接读取并归一化 Properties")
    void getCurrent_readsProperties() {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setMaxSizeBytes(10_485_760L);
        properties.setAllowedSuffixes(new LinkedHashSet<>(List.of("PDF", "md")));

        DocumentFileRuntimeConfig result = new DocumentFileRuntimeConfigServiceImpl(properties).getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(10_485_760L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "md");
    }
}
