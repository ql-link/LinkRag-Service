package com.qingluo.link.service.mq.config;

import com.qingluo.link.service.support.CdcBridgeMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

/**
 * {@link CdcBridgeKafkaConfig} 失败分类与 recover 单测。
 *
 * <p>覆盖 acceptance：永久错误（IllegalArgumentException / DeserializationException）判 bad_payload，
 * 其余判 infra_exhausted；recover 记录指标且不抛。</p>
 */
@ExtendWith(MockitoExtension.class)
class CdcBridgeKafkaConfigTest {

    private final CdcBridgeKafkaConfig config = new CdcBridgeKafkaConfig();

    @Mock
    private CdcBridgeMetrics metrics;

    @Test
    @DisplayName("坏消息异常归类为 bad_payload（不可重试）")
    void classify_badPayload() {
        assertThat(config.classify(new IllegalArgumentException("bad")))
                .isEqualTo("bad_payload");
        assertThat(config.classify(new DeserializationException("x", new byte[0], false, new RuntimeException())))
                .isEqualTo("bad_payload");
    }

    @Test
    @DisplayName("其他异常归类为 infra_exhausted（退避耗尽）")
    void classify_infraExhausted() {
        assertThat(config.classify(new RuntimeException("io error")))
                .isEqualTo("infra_exhausted");
    }

    @Test
    @DisplayName("recover：记录指标且永不抛出")
    void recover_recordsMetricAndNeverThrows() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("tolink.canal.binlog", 0, 0L, "k", "bad-payload");

        assertThatCode(() -> config.recover(record, new IllegalArgumentException("bad json"), metrics))
                .doesNotThrowAnyException();
        verify(metrics).recordRecover("bad_payload");
    }

    @Test
    @DisplayName("错误处理器可正常构建")
    void errorHandlerCreated() {
        assertThat(config.cdcBridgeErrorHandler(metrics)).isNotNull();
    }
}
