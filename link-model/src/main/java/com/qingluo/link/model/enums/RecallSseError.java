package com.qingluo.link.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 召回对前端 SSE {@code error} 事件的错误码（英文串码）。
 *
 * <p>决策①：对前端 SSE 用英文串码，对内沿用数字 {@link ErrorCode}。本枚举承载“建流后”SSE error 的 code，
 * 并提供 Python 上游错误的透传/兜底映射（决策⑤：已知码透传，未知 code 或非 2xx 兜底 {@link #RECALL_UPSTREAM_ERROR}，
 * 不向前端暴露内部细节）。</p>
 */
@Getter
@AllArgsConstructor
public enum RecallSseError {

    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已失效"),
    RECALL_SCOPE_FORBIDDEN("RECALL_SCOPE_FORBIDDEN", "无权访问指定数据集"),
    RECALL_INVALID_REQUEST("RECALL_INVALID_REQUEST", "召回请求参数不合法"),
    RECALL_INTERNAL_AUTH_FAILED("RECALL_INTERNAL_AUTH_FAILED", "召回内部鉴权失败"),
    RECALL_ALL_SOURCES_FAILED("RECALL_ALL_SOURCES_FAILED", "召回失败，请稍后再试"),
    RECALL_TIMEOUT("RECALL_TIMEOUT", "召回超时，请稍后再试"),
    RECALL_UPSTREAM_ERROR("RECALL_UPSTREAM_ERROR", "召回服务暂不可用，请稍后再试");

    private final String code;
    private final String defaultMessage;

    /**
     * Python 上游 {@code error} 事件 code 的映射：已知码透传，未知码兜底 {@link #RECALL_UPSTREAM_ERROR}。
     */
    public static RecallSseError fromUpstreamCode(String upstreamCode) {
        if (upstreamCode != null) {
            for (RecallSseError error : values()) {
                if (error.code.equals(upstreamCode)) {
                    return error;
                }
            }
        }
        return RECALL_UPSTREAM_ERROR;
    }

    /**
     * Python “建流前”HTTP 非 2xx 的映射：401/403→内部鉴权失败，504→超时，其余→兜底。
     */
    public static RecallSseError fromUpstreamHttpStatus(int httpStatus) {
        if (httpStatus == 401 || httpStatus == 403) {
            return RECALL_INTERNAL_AUTH_FAILED;
        }
        if (httpStatus == 504) {
            return RECALL_TIMEOUT;
        }
        return RECALL_UPSTREAM_ERROR;
    }
}
