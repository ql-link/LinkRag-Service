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
    MODEL_CONFIG_INCOMPLETE(10014, "模型能力缺少协议或入口，无法保存或展开", 400),
    INVALID_PROTOCOL(10015, "协议不在支持范围内", 400),

    // 用户/认证相关 (20001-29999)
    USER_NOT_FOUND(20001, "用户不存在", 404),
    INVALID_PASSWORD(20002, "密码错误", 401),
    AUTH_DISABLED(20003, "账号已被禁用", 403),
    CONVERSATION_NOT_FOUND(20004, "对话不存在", 404),
    UNAUTHORIZED_ACCESS(20005, "无权访问该对话内容", 403),
    DUPLICATE_USERNAME(20006, "用户名已存在", 409),
    DUPLICATE_EMAIL(20007, "邮箱已被使用", 409),

    // 召回 / RAG 相关 (30001-39999)
    // 召回 session 签发链路的数据集归属校验（前端直连 Python 召回，LINK-104）。
    // 旧召回网关链路（Java 同步转发 Python 内部召回端点）已于 LINK-122 废弃，相关错误码一并移除。
    RECALL_SCOPE_FORBIDDEN(30002, "无权访问指定数据集", 403),

    // 系统错误 (50001-59999)
    UNKNOWN_ERROR(50001, "系统内部错误", 500),
    CACHE_DELETE_FAILED(50002, "缓存删除失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
