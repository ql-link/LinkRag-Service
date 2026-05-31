package com.qingluo.link.service.mq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.exception.ParseResultPendingException;
import com.qingluo.link.service.support.ParseResultMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

class ParseResultKafkaConfigTest {

    private final ParseResultKafkaConfig config = new ParseResultKafkaConfig();

    @Test
    void Should_BuildSeekToCurrentErrorHandler() {
        ParseResultMetrics metrics = mock(ParseResultMetrics.class);

        SeekToCurrentErrorHandler handler = config.parseResultErrorHandler(metrics);

        assertThat(handler).isNotNull();
    }

    @Test
    void Should_ClassifyExceptionReasons() {
        assertThat(config.classify(new NonRetryableParseResultException("x"))).isEqualTo("non_retryable");
        assertThat(config.classify(new ParseResultPendingException("x"))).isEqualTo("pending_exhausted");
        assertThat(config.classify(new IllegalArgumentException("x"))).isEqualTo("bad_payload");
        assertThat(config.classify(new DeserializationException("x", new byte[0], false, null)))
            .isEqualTo("bad_payload");
        assertThat(config.classify(new DataAccessResourceFailureException("db down"))).isEqualTo("infra_exhausted");
    }

    @Test
    void Should_RecoverWithoutThrowing_AndRecordMetric_UnwrappingListenerException() {
        ParseResultMetrics metrics = mock(ParseResultMetrics.class);
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.rag.parse_result", 0, 0L, "k", "{bad}");
        // 模拟 spring-kafka 包装后的异常链，验证 recover 能解包到根因并分类
        Exception wrapped = new ListenerExecutionFailedException(
            "listener failed", new NonRetryableParseResultException("mismatch"));

        assertThatCode(() -> config.recover(record, wrapped, metrics)).doesNotThrowAnyException();
        verify(metrics).recordRecover("non_retryable");
    }
}
