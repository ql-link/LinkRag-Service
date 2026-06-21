package com.qingluo.link.components.mq.constant;

import java.util.Arrays;

/**
 * 全链路用量调用阶段（{@code llm_usage_log.stage} 取值的唯一来源）。
 *
 * <p>由 {@code usage_report} 消息契约校验与 {@code chat_turn} 落库共享，避免 {@code "parse"}/{@code "recall"}/{@code "chat"}
 * 字面量在消息模型、落库服务、查询服务多处各写一份而漂移。</p>
 */
public enum UsageStage {

    PARSE("parse"),
    RECALL("recall"),
    CHAT("chat");

    private final String code;

    UsageStage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 判断给定阶段码是否为合法取值。
     */
    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(stage -> stage.code.equals(code));
    }
}
