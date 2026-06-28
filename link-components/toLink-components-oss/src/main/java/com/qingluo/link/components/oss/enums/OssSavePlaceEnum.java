package com.qingluo.link.components.oss.enums;

/**
 * 上传对象的存储可见性。
 *
 * <p>PUBLIC  —— 单一匿名可读公开桶（tolink-public），承接所有不敏感资源
 * （博客、反馈、avatar、chatImage）；上传返回完整公开 URL。
 * RAW     —— 原文件桶（tolink-rag-raw），专门存储用户上传的原始文件；Python 只读，Java 写入；
 *            桶不可匿名访问，上传返回对象 key。
 * PRIVATE —— 解析产物桶（tolink-rag-docs），由 Python 写入 Markdown 与图片；Java 只读；
 *            桶不可匿名访问，上传返回对象 key。</p>
 */
public enum OssSavePlaceEnum {
    PUBLIC,
    RAW,
    PRIVATE
}
