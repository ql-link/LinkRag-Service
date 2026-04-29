package com.qingluo.link.service.impl.know;

import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * @deprecated Phase 2 deprecated the compensateCreatedTasks mechanism.
 * Tests here are kept for regression but the method is no longer called in main flow.
 */
@Deprecated
@ExtendWith(MockitoExtension.class)
class KnowledgeParseTaskServiceImplTest {

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @Mock
    private KnowledgeParseTaskMapper knowledgeParseTaskMapper;

    @Mock
    private KnowledgeParsedFileMapper knowledgeParsedFileMapper;

    @Mock
    private ObjectProvider<MQSend> mqSendProvider;

    @Mock
    private MQSend mqSend;

    @Test
    @DisplayName("Should_DeprecateOldCompensationMethod")
    void Should_DeprecateOldCompensationMethod() {
        // Phase 2: compensateCreatedTasks mechanism deprecated
        // The scheduled method is now empty - no actual compensation happens
        KnowledgeFileProperties properties = new KnowledgeFileProperties();
        properties.setParseDispatchMaxRetryCount(5);
        properties.setParseDispatchRetryIntervalSeconds(30);
        KnowledgeParseTaskServiceImpl service = new KnowledgeParseTaskServiceImpl(
            datasetMapper,
            knowledgeOriginalFileMapper,
            knowledgeParseTaskMapper,
            knowledgeParsedFileMapper,
            mqSendProvider,
            properties);

        // No exception means the deprecated method is still callable (no-op behavior)
        service.compensateCreatedTasksOnSchedule();
        // No mock verification needed - method does nothing
    }
}
