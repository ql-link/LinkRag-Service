package com.qingluo.link.service.exception;

/**
 * parse_result 消费中“业务不可恢复”的异常。
 *
 * <p>表示消息与 Python 已持久化的 document_parsed_log 存在逻辑矛盾
 * （task_id / 状态 / 归属不匹配）。这类问题重试也不会变对，
 * 因此被错误处理器登记为 not-retryable，立即 recover（告警 + 提交跳过），
 * 不进入退避重试，避免毫秒级空转。</p>
 */
public class NonRetryableParseResultException extends RuntimeException {

    public NonRetryableParseResultException(String message) {
        super(message);
    }
}
