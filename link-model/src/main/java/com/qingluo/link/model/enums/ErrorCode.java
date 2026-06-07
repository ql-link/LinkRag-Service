package com.qingluo.link.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 错误码分类：
 * - 10001-19999: LLM 配置相关
 * - 20001-29999: 用户/认证相关
 * - 30001-39999: 召回 / RAG 网关相关
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
    DOCUMENT_FILE_CONFIG_INVALID(10010, "文档文件上传配置不合法", 400),
    INVALID_MODEL_CAPABILITY(10011, "模型能力标识无效", 400),
    MODEL_DISABLED(10012, "模型已被关闭，不可选为生效", 400),
    PRESET_READONLY(10013, "系统预设为只读，不可修改或删除", 403),

    // 用户/认证相关 (20001-29999)
    USER_NOT_FOUND(20001, "用户不存在", 404),
    INVALID_PASSWORD(20002, "密码错误", 401),
    AUTH_DISABLED(20003, "账号已被禁用", 403),
    CONVERSATION_NOT_FOUND(20004, "对话不存在", 404),
    UNAUTHORIZED_ACCESS(20005, "无权访问该对话内容", 403),
    DUPLICATE_USERNAME(20006, "用户名已存在", 409),
    DUPLICATE_EMAIL(20007, "邮箱已被使用", 409),

    // 召回 / RAG 网关相关 (30001-39999)
    // 用于建流前 HTTP 错误（数字 code + httpStatus）；建流后 SSE 的英文串码见 RecallSseError。
    RECALL_INVALID_REQUEST(30001, "召回请求参数不合法", 400),
    RECALL_SCOPE_FORBIDDEN(30002, "无权访问指定数据集", 403),
    RECALL_RATE_LIMITED(30003, "召回请求过于频繁，请稍后再试", 429),
    RECALL_INTERNAL_AUTH_FAILED(30004, "召回内部鉴权失败", 502),
    RECALL_ALL_SOURCES_FAILED(30005, "召回失败，请稍后再试", 502),
    RECALL_TIMEOUT(30006, "召回超时，请稍后再试", 504),
    RECALL_UPSTREAM_ERROR(30007, "召回服务暂不可用，请稍后再试", 502),

    // 系统错误 (50001-59999)
    UNKNOWN_ERROR(50001, "系统内部错误", 500),
    CACHE_DELETE_FAILED(50002, "缓存删除失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
