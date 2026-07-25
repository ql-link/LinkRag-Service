package com.qingluo.link.service.impl.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.qingluo.link.service.config.DocumentFileConfigSnapshot;
import com.qingluo.link.service.config.DocumentFileConfigStore;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentFileRuntimeConfigServiceImplTest {

    @Mock private DocumentFileConfigStore store;

    @Test
    void getCurrent_usesResolvedRuntimeSnapshot() {
        when(store.resolve()).thenReturn(new DocumentFileConfigSnapshot(
            10_485_760L, new LinkedHashSet<>(List.of("pdf", "md")), 3L, null));
        DocumentFileRuntimeConfig result = new DocumentFileRuntimeConfigServiceImpl(store).getCurrent();
        assertThat(result.getMaxSizeBytes()).isEqualTo(10_485_760L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "md");
        assertThat(result.getUpdatedBy()).isEqualTo(3L);
    }
}
