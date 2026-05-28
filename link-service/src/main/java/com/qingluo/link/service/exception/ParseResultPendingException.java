package com.qingluo.link.service.exception;

/**
 * parse_result 消费中“暧昧、可重试”的异常。
 *
 * <p>当前唯一来源：消息已到达但 Java 侧暂时查不到对应的 document_parsed_log。
 * Python 是先写日志再发消息，正常应能查到；查不到通常是跨库写可见性 /
 * 主从延迟带来的瞬时缺失，因此按可重试处理——错误处理器对其执行带退避重试，
 * 若重试窗口内日志出现则正常处理，仍缺失则 recover（告警 + 提交跳过）。</p>
 */
public class ParseResultPendingException extends RuntimeException {

    public ParseResultPendingException(String message) {
        super(message);
    }
}
