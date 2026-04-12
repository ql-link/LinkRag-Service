package com.qingluo.link.core.enums;

/**
 * 业务错误码枚举
 *
 * 分段规则：
 *   1xxxx  LLM 配置 / 厂商相关
 *   2xxxx  用户 / 认证 / 对话相关
 *   5xxxx  系统级（供 SystemException 使用）
 */
public enum ErrorCode {

    // ========== LLM 厂商 & 配置 (10001-19999) ==========

    PROVIDER_NOT_FOUND(10001, "系统厂商不存在"),

    PROVIDER_DISABLED(10002, "系统厂商已被禁用"),

    PROVIDER_IN_USE(10003, "厂商被用户使用中，无法删除"),

    USER_CONFIG_NOT_FOUND(10004, "用户配置不存在"),

    USER_CONFIG_DISABLED(10005, "用户配置已被禁用"),

    NO_DEFAULT_CONFIG(10006, "用户没有设置默认配置"),

    INVALID_API_KEY(10007, "API Key 格式无效"),

    MODEL_NOT_SUPPORTED(10008, "模型不被该厂商支持"),

    DUPLICATE_USER_CONFIG(10009, "用户已存在该厂商相同模型的配置"),


    // ========== 用户 / 认证 / 对话 (20001-29999) ==========

    USER_NOT_FOUND(20001, "用户不存在"),

    INVALID_PASSWORD(20002, "密码错误"),

    AUTH_DISABLED(20003, "账号已被禁用"),

    CONVERSATION_NOT_FOUND(20004, "对话不存在"),

    UNAUTHORIZED_ACCESS(20005, "无权访问该对话内容"),


    // ========== 系统级（供 SystemException 使用，不限于特定业务） ==========

    UNKNOWN_ERROR(50001, "系统内部错误");


    private final int code;

    private final String message;


    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }


    public int getCode() {
        return code;
    }


    public String getMessage() {
        return message;
    }
}