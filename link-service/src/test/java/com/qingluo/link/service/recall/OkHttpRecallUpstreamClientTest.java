package com.qingluo.link.service.recall;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.RecallHitDTO;
import com.qingluo.link.model.enums.RecallSseError;
import com.qingluo.link.service.config.RecallProperties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 上游 okhttp 客户端解析/映射（acceptance 场景 16/17/18/19/20/22）。用 MockWebServer 模拟 Python。
 */
class OkHttpRecallUpstreamClientTest {

    private MockWebServer server;
    private ObjectMapper objectMapper;
    private RecallProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        properties = new RecallProperties();
        properties.setPythonBaseUrl(server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private OkHttpRecallUpstreamClient client(Executor executor, long callTimeoutMs) {
        OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            .build();
        return new OkHttpRecallUpstreamClient(executor, http, objectMapper, properties);
    }

    private RecallUpstreamRequest request() {
        return new RecallUpstreamRequest("q", 100L, List.of(1L, 2L));
    }

    @Test
    @DisplayName("Should_TrimHitsAndCallOnDone_When_RecallDone")
    void Should_TrimHitsAndCallOnDone_When_RecallDone() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: recall_done\n"
                + "data: {\"hits\":[{\"chunk_id\":\"c1\",\"doc_id\":10,\"dataset_id\":1,\"fused_score\":0.9}],"
                + "\"failed_sources\":[]}\n\n"));

        CapturingListener listener = new CapturingListener();
        client(Runnable::run, 5000).stream(request(), "jwt", "req-1", listener);

        assertThat(listener.await()).isTrue();
        assertThat(listener.error).isNull();
        assertThat(listener.doneHits).hasSize(1);
        RecallHitDTO hit = listener.doneHits.get(0);
        assertThat(hit.getChunkId()).isEqualTo("c1");
        assertThat(hit.getDocId()).isEqualTo(10L);
        assertThat(hit.getDatasetId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should_PassthroughKnownCode_When_UpstreamError")
    void Should_PassthroughKnownCode_When_UpstreamError() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: error\ndata: {\"code\":\"RECALL_ALL_SOURCES_FAILED\",\"message\":\"all failed\"}\n\n"));

        CapturingListener listener = new CapturingListener();
        client(Runnable::run, 5000).stream(request(), "jwt", "req-1", listener);

        assertThat(listener.await()).isTrue();
        assertThat(listener.error).isEqualTo(RecallSseError.RECALL_ALL_SOURCES_FAILED);
    }

    @Test
    @DisplayName("Should_MapToInternalAuthFailed_When_Upstream401")
    void Should_MapToInternalAuthFailed_When_Upstream401() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));

        CapturingListener listener = new CapturingListener();
        client(Runnable::run, 5000).stream(request(), "jwt", "req-1", listener);

        assertThat(listener.await()).isTrue();
        assertThat(listener.error).isEqualTo(RecallSseError.RECALL_INTERNAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("Should_MapToTimeout_When_SlowBeyondCallTimeout")
    void Should_MapToTimeout_When_SlowBeyondCallTimeout() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: recall_done\ndata: {\"hits\":[]}\n\n")
            .setBodyDelay(2, TimeUnit.SECONDS));

        CapturingListener listener = new CapturingListener();
        client(Runnable::run, 300).stream(request(), "jwt", "req-1", listener);

        assertThat(listener.await()).isTrue();
        assertThat(listener.error).isEqualTo(RecallSseError.RECALL_TIMEOUT);
    }

    @Test
    @DisplayName("Should_NotInvokeListener_When_Canceled")
    void Should_NotInvokeListener_When_Canceled() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: recall_done\ndata: {\"hits\":[]}\n\n")
            .setBodyDelay(2, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CapturingListener listener = new CapturingListener();
            RecallUpstreamCall call = client(executor, 5000).stream(request(), "jwt", "req-1", listener);
            Thread.sleep(200);
            call.cancel();
            // 取消后不应回调 onDone/onError（前端断连静默）
            assertThat(listener.latch.await(1, TimeUnit.SECONDS)).isFalse();
            assertThat(listener.doneHits).isNull();
            assertThat(listener.error).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    static class CapturingListener implements RecallUpstreamListener {
        volatile List<RecallHitDTO> doneHits;
        volatile RecallSseError error;
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onDone(List<RecallHitDTO> hits) {
            this.doneHits = hits;
            latch.countDown();
        }

        @Override
        public void onError(RecallSseError error) {
            this.error = error;
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }
}
