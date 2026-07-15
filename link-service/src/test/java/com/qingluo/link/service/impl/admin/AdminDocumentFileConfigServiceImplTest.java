package com.qingluo.link.service.impl.admin;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDocumentFileConfigServiceImplTest {

    @Test
    @DisplayName("管理端只读配置返回当前 Properties 且无运行时更新元数据")
    void getCurrentConfig_readsProperties() {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setMaxSizeBytes(10_485_760L);
        properties.setAllowedSuffixes(new LinkedHashSet<>(List.of("PDF", "md")));

        DocumentFileConfigDTO result = new AdminDocumentFileConfigServiceImpl(properties).getCurrentConfig();

        assertThat(result.getMaxSizeBytes()).isEqualTo(10_485_760L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "md");
        assertThat(result.getUpdatedBy()).isNull();
        assertThat(result.getUpdatedAt()).isNull();
    }
}
