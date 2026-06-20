package com.qingluo.link.components.mq.constant;

import java.util.Arrays;

/**
 * 全链路用量调用操作（{@code llm_usage_log.operation} 取值的唯一来源）。
 *
 * <p>由 {@code usage_report} 消息契约校验与 {@code chat_turn} 落库共享。{@code sparse} 因模型不返回 token 本期预留不上报，
 * 故不在合法取值内；{@code generate} 走 {@code chat_turn} 通道补写，仍属合法值以保证全链路口径一致。</p>
 */
public enum UsageOperation {

    EMBED("embed"),
    RERANK("rerank"),
    VISION("vision"),
    TABLE("table"),
    GENERATE("generate");

    private final String code;

    UsageOperation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 判断给定操作码是否为合法取值。
     */
    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(operation -> operation.code.equals(code));
    }
}
