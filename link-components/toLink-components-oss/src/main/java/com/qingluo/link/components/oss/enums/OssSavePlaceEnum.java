package com.qingluo.link.components.oss.enums;

/**
 * 上传对象的存储可见性。
 *
 * <p>PUBLIC —— 单一匿名可读公开桶（tolink-public），承接所有不敏感资源
 * （博客、反馈、avatar、chatImage）；上传返回完整公开 URL。
 * PRIVATE —— 私有桶（tolink-rag-docs），承接 RAG 文档；桶不可匿名访问，上传返回对象 key。</p>
 */
public enum OssSavePlaceEnum {
    PUBLIC,
    PRIVATE
}
