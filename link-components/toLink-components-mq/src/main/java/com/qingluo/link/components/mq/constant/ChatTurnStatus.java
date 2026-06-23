package com.qingluo.link.components.mq.constant;

import java.util.Arrays;

/**
 * 对话轮次状态（{@code chat_message.status} 与 {@code ChatTurnMQ.status} 取值的唯一来源）。
 *
 * <p>一轮问答由「起点 {@link #GENERATING} + 终态（{@link #COMPLETED} / {@link #FAILED}）」至少两条同
 * {@code turn_id} 的消息组成，Java 按 {@code turn_id} upsert 同一行。旧值 {@code success}/{@code partial}/{@code failed}
 * 已退役。</p>
 *
 * <p>状态优先级：{@code GENERATING}(0) &lt; 终态(1)。终态写入后不再被迟到的 {@code GENERATING} 覆盖。</p>
 */
public enum ChatTurnStatus {

    /** 生成起点：Python 发出，answer 为空，Java 插入「生成中」行。 */
    GENERATING(false),
    /** 生成成功终态（含 0 命中空占位）。 */
    COMPLETED(true),
    /** 生成失败终态：携带 error_code(+error_message)。 */
    FAILED(true);

    private final boolean terminal;

    ChatTurnStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /** 是否为终态（COMPLETED / FAILED）。 */
    public boolean isTerminal() {
        return terminal;
    }

    /** 判断给定状态码是否为合法取值。 */
    public static boolean isValid(String code) {
        return code != null && Arrays.stream(values()).anyMatch(s -> s.name().equals(code));
    }

    /**
     * 解析状态码为枚举，非法值抛出 {@link IllegalArgumentException}。
     */
    public static ChatTurnStatus from(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("chat_turn status is invalid: " + code);
        }
        return valueOf(code);
    }
}
