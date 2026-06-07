package com.qingluo.link.core.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪上下文工具。
 *
 * <p>统一管理 traceId 在 SLF4J {@link MDC} 中的读写：HTTP 入口由 {@link TraceIdFilter} 注入，
 * 异步线程由 {@link MdcTaskDecorator} 透传，MQ 消费者 / 定时任务等无 HTTP 上下文的入口
 * 调用 {@link #startNew()} 自建一个 traceId。日志格式中以 {@code %X{traceId}} 输出。</p>
 */
public final class TraceContext {

    /** MDC 中 traceId 的键，须与 logback pattern 的 %X{traceId} 一致。 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 上下游透传 traceId 的 HTTP 头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 合法 traceId 字符集与长度上限：仅字母数字/下划线/连字符，1~64 位。 */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private TraceContext() {
    }

    /** 生成一个新的 traceId（无连字符的 UUID）。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验上游传入的 traceId 是否合法（防日志注入 / CWE-117）：仅允许字母数字、下划线、连字符，长度 1~64。
     * 含换行等控制字符或超长值会被判非法，由调用方改为新建，避免污染日志文件。
     */
    public static boolean isValid(String traceId) {
        return traceId != null && VALID_TRACE_ID.matcher(traceId).matches();
    }

    /** 将指定 traceId 写入当前线程 MDC。 */
    public static void put(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /** 为无 HTTP 上下文的入口（MQ 消费者、定时任务）新建并写入一个 traceId，返回该值。 */
    public static String startNew() {
        String traceId = newTraceId();
        put(traceId);
        return traceId;
    }

    /** 清理当前线程 MDC 中的 traceId，必须在入口 finally 中调用，避免线程复用串号。 */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
