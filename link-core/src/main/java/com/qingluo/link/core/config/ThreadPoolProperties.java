package com.qingluo.link.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池多池配置根：{@code thread-pool.<池名>.*}。
 *
 * <p>每个业务一个专用池，互不共用。本次仅 {@code document-upload}；未来加池 =
 * 在此新增一个 {@link PoolProperties} 字段 + 在 {@link ThreadPoolConfig} 新增一个 {@code @Bean}。</p>
 */
@ConfigurationProperties(prefix = "thread-pool")
public class ThreadPoolProperties {

    /** 文档上传专用池：thread-pool.document-upload.* */
    private PoolProperties documentUpload = new PoolProperties();

    public PoolProperties getDocumentUpload() {
        return documentUpload;
    }

    public void setDocumentUpload(PoolProperties documentUpload) {
        this.documentUpload = documentUpload;
    }
}
