package com.qingluo.link.service.recall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.RecallHitDTO;
import com.qingluo.link.model.enums.RecallSseError;
import com.qingluo.link.service.config.RecallProperties;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 用 okhttp 4.x 调用 Python 内部召回 stream，并把上游 SSE 解析/映射为 {@link RecallUpstreamListener} 回调。
 *
 * <p>在召回转发线程池内同步 {@code execute()}（不受 okhttp dispatcher maxRequestsPerHost 限制），
 * {@link #stream} 立即返回可取消句柄。okhttp 无 okhttp-sse，故手动逐行解析 text/event-stream。
 * 首版 Python 每次返回单个事件（recall_done 或 error）后结束。</p>
 */
@Component
@Slf4j
public class OkHttpRecallUpstreamClient implements RecallUpstreamClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String INTERNAL_PATH = "/api/v1/internal/recall/stream";

    private final Executor executor;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final RecallProperties properties;

    public OkHttpRecallUpstreamClient(@Qualifier("recallStreamExecutor") Executor executor,
                                      @Qualifier("recallOkHttpClient") OkHttpClient okHttpClient,
                                      ObjectMapper objectMapper,
                                      RecallProperties properties) {
        this.executor = executor;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public RecallUpstreamCall stream(RecallUpstreamRequest request, String jwt, String requestId,
                                     RecallUpstreamListener listener) {
        Call call = okHttpClient.newCall(buildRequest(request, jwt, requestId));
        // 前端主动取消与 okhttp callTimeout 内部取消都会让 call.isCanceled()=true，但只有前者应静默、
        // 后者应映射为 TIMEOUT。用本标志区分“是否前端主动取消”。
        AtomicBoolean canceledByClient = new AtomicBoolean(false);
        // 池满 → RejectedExecutionException 抛给调用方（RecallServiceImpl 转 SSE error）。
        executor.execute(() -> doStream(call, canceledByClient, requestId, listener));
        return () -> {
            canceledByClient.set(true);
            call.cancel();
        };
    }

    private Request buildRequest(RecallUpstreamRequest request, String jwt, String requestId) {
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new IllegalStateException("序列化召回上游请求失败", e);
        }
        return new Request.Builder()
            .url(properties.getPythonBaseUrl() + INTERNAL_PATH)
            .header("Authorization", "Bearer " + jwt)
            .header("X-Request-Id", requestId)
            .header("Accept", "text/event-stream")
            .post(RequestBody.create(JSON, json))
            .build();
    }

    private void doStream(Call call, AtomicBoolean canceledByClient, String requestId, RecallUpstreamListener listener) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                // Python 建流前 HTTP 非 2xx：记录 status + requestId 便于排查，映射为前端 error。
                log.warn("Python 召回返回非 2xx, status={}, requestId={}", response.code(), requestId);
                listener.onError(RecallSseError.fromUpstreamHttpStatus(response.code()));
                return;
            }
            ResponseBody body = response.body();
            if (body == null) {
                listener.onError(RecallSseError.RECALL_UPSTREAM_ERROR);
                return;
            }
            parseSse(body.source(), listener, requestId);
        } catch (IOException e) {
            if (canceledByClient.get()) {
                // 前端主动断连导致的取消：非业务错误，静默（前端 SSE 已由 RecallServiceImpl 关闭）。
                log.debug("Python 召回 stream 已取消（前端断连）, requestId={}", requestId);
                return;
            }
            if (e instanceof SocketTimeoutException || e instanceof InterruptedIOException) {
                // 含 okhttp callTimeout 超时（okhttp 内部会 cancel，但 canceledByClient 仍为 false）。
                log.warn("Python 召回超时, requestId={}", requestId);
                listener.onError(RecallSseError.RECALL_TIMEOUT);
            } else {
                log.warn("Python 召回调用异常, requestId={}", requestId, e);
                listener.onError(RecallSseError.RECALL_UPSTREAM_ERROR);
            }
        }
    }

    /**
     * 逐行解析 text/event-stream，聚合一条完整事件（event + data，空行分隔）后分发。首版单事件即结束。
     */
    private void parseSse(BufferedSource source, RecallUpstreamListener listener, String requestId) throws IOException {
        String eventName = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = source.readUtf8Line()) != null) {
            if (line.isEmpty()) {
                if (eventName != null) {
                    dispatchEvent(eventName, data.toString(), listener, requestId);
                    return;
                }
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
            // 其余行（": " 注释、id: 等）忽略
        }
        // 流结束但无空行分隔：用已聚合的事件兜底。
        if (eventName != null) {
            dispatchEvent(eventName, data.toString(), listener, requestId);
        } else {
            log.warn("Python 召回 stream 未返回有效事件, requestId={}", requestId);
            listener.onError(RecallSseError.RECALL_UPSTREAM_ERROR);
        }
    }

    private void dispatchEvent(String eventName, String data, RecallUpstreamListener listener, String requestId) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if ("recall_done".equals(eventName)) {
                listener.onDone(extractHits(root));
            } else if ("error".equals(eventName)) {
                // 已知码透传，未知码兜底（决策⑤）。
                listener.onError(RecallSseError.fromUpstreamCode(root.path("code").asText(null)));
            } else {
                log.warn("Python 召回返回未知事件 event={}, requestId={}", eventName, requestId);
                listener.onError(RecallSseError.RECALL_UPSTREAM_ERROR);
            }
        } catch (IOException e) {
            log.warn("解析 Python 召回事件失败 event={}, requestId={}", eventName, requestId, e);
            listener.onError(RecallSseError.RECALL_UPSTREAM_ERROR);
        }
    }

    /**
     * 裁剪上游 hits 为前端最小候选，保持顺序，丢弃 fused_score/scores/failed_sources。
     *
     * <p>字段名 chunk_id/doc_id/dataset_id <b>待与 Python brief 对齐</b>（TD §12-3）。</p>
     */
    private List<RecallHitDTO> extractHits(JsonNode root) {
        List<RecallHitDTO> hits = new ArrayList<>();
        JsonNode array = root.path("hits");
        if (array.isArray()) {
            for (JsonNode hit : array) {
                String chunkId = hit.path("chunk_id").asText(null);
                Long docId = hit.hasNonNull("doc_id") ? hit.get("doc_id").asLong() : null;
                Long datasetId = hit.hasNonNull("dataset_id") ? hit.get("dataset_id").asLong() : null;
                hits.add(new RecallHitDTO(chunkId, docId, datasetId));
            }
        }
        return hits;
    }
}
