package com.qingluo.link.observability.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪上下文工具。
 *
 * <p>统一管理 trace_id 在 SLF4J {@link MDC} 中的读写：HTTP 入口由 {@link TraceIdFilter} 注入，
 * 异步线程由 {@link MdcTaskDecorator} 透传，MQ 消费者 / 定时任务等无 HTTP 上下文的入口
 * 调用 {@link #startNew()} 自建一个 trace_id。日志 JSON 中以顶层 {@code trace_id} 输出。</p>
 */
public final class TraceContext {

    /** MDC 中 trace_id 的主键，须与 Java/Python 统一日志字段名一致。 */
    public static final String TRACE_ID_KEY = "trace_id";

    /** 旧日志实现使用的 MDC 键，短期双写以兼容仍读取 traceId 的本地代码或临时配置。 */
    public static final String LEGACY_TRACE_ID_KEY = "traceId";

    /** 上下游透传 trace_id 的 HTTP / MQ 头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 合法 trace_id 字符集与长度上限：仅字母数字/下划线/连字符，1~64 位。 */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private TraceContext() {
    }

    /** 生成一个新的 trace_id（无连字符的 UUID）。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验上游传入的 trace_id 是否合法（防日志注入 / CWE-117）：仅允许字母数字、下划线、连字符，长度 1~64。
     * 含换行等控制字符或超长值会被判非法，由调用方改为新建，避免污染日志文件。
     */
    public static boolean isValid(String traceId) {
        return traceId != null && VALID_TRACE_ID.matcher(traceId).matches();
    }

    /** 将指定 trace_id 写入当前线程 MDC。 */
    public static void put(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(LEGACY_TRACE_ID_KEY, traceId);
    }

    /** 返回当前线程 MDC 中的 trace_id，兼容读取旧 traceId 键。 */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : MDC.get(LEGACY_TRACE_ID_KEY);
    }

    /** 使用上游 trace_id 初始化当前入口；非法或缺失时新建并返回实际使用的值。 */
    public static String start(String traceId) {
        String effectiveTraceId = isValid(traceId) ? traceId : newTraceId();
        put(effectiveTraceId);
        return effectiveTraceId;
    }

    /** 为无 HTTP 上下文的入口（MQ 消费者、定时任务）新建并写入一个 trace_id，返回该值。 */
    public static String startNew() {
        return start(null);
    }

    /** 清理当前线程 MDC 中的 trace_id，必须在入口 finally 中调用，避免线程复用串号。 */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(LEGACY_TRACE_ID_KEY);
    }
}
