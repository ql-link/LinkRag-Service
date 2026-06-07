package com.qingluo.link.core.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * 安全/合规审计日志工具：为高危动作（登录、注册、改角色/状态、配置厂商 Key、删除等）统一留痕。
 *
 * <p>使用专用 logger 名 {@code AUDIT}，可在 logback 中单独调级或拆独立 appender；输出统一带
 * {@code action=}，并复用 MDC 的 traceId（日志格式 {@code [%X{traceId}]}）串联到具体请求与操作人。
 * 仅记录可追溯所需的标识与结果，<strong>严禁记录明文密码、API Key、token 等敏感值</strong>。</p>
 */
public final class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private AuditLog() {
    }

    /**
     * 记录一条审计事件。
     *
     * @param action 动作标识（大写蛇形，如 {@code LOGIN_SUCCESS}、{@code USER_ROLE_CHANGE}）
     * @param detail 明细模板，使用 SLF4J 的 {@code {}} 占位
     * @param args   占位参数（仅传标识/结果，勿传敏感值）
     */
    public static void event(String action, String detail, Object... args) {
        if (log.isInfoEnabled()) {
            String rendered = MessageFormatter.arrayFormat(detail, args).getMessage();
            log.info("action={} | {}", action, rendered);
        }
    }
}
