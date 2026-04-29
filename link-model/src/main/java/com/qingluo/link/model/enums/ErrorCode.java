package com.qingluo.link.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 错误码分类：
 * - 10001-19999: LLM 配置相关
 * - 20001-29999: 用户/认证相关
 * - 50001-59999: 系统错误
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // LLM 配置相关 (10001-19999)
    PROVIDER_NOT_FOUND(10001, "系统厂商不存在", 404),
    PROVIDER_DISABLED(10002, "系统厂商已被禁用", 400),
    PROVIDER_IN_USE(10003, "厂商被用户使用中，无法删除", 409),
    USER_CONFIG_NOT_FOUND(10004, "用户配置不存在", 404),
    USER_CONFIG_DISABLED(10005, "用户配置已被禁用", 400),
    NO_DEFAULT_CONFIG(10006, "用户没有设置默认配置", 404),
    INVALID_API_KEY(10007, "API Key 格式无效", 400),
    MODEL_NOT_SUPPORTED(10008, "模型不被该厂商支持", 400),
    DUPLICATE_USER_CONFIG(10009, "用户已存在该厂商相同模型的配置", 409),
    KNOWLEDGE_FILE_CONFIG_INVALID(10010, "知识文件上传配置不合法", 400),
    INVALID_MODEL_CAPABILITY(10011, "模型能力不合法", 400),

    // 用户/认证相关 (20001-29999)
    USER_NOT_FOUND(20001, "用户不存在", 404),
    INVALID_PASSWORD(20002, "密码错误", 401),
    AUTH_DISABLED(20003, "账号已被禁用", 403),
    CONVERSATION_NOT_FOUND(20004, "对话不存在", 404),
    UNAUTHORIZED_ACCESS(20005, "无权访问该对话内容", 403),
    DUPLICATE_USERNAME(20006, "用户名已存在", 409),
    DUPLICATE_EMAIL(20007, "邮箱已被使用", 409),

    // 系统错误 (50001-59999)
    UNKNOWN_ERROR(50001, "系统内部错误", 500),
    CACHE_DELETE_FAILED(50002, "缓存删除失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
