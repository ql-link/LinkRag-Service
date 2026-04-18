package com.qingluo.link.components.oss.enums;

/**
 * Supported OSS provider names used by tolink.oss.service-type.
 */
public enum OssServiceTypeEnum {
    LOCAL("local"),
    ALIYUN_OSS("aliyun-oss"),
    MINIO("minio"),
    S3("s3"),
    COS("cos");

    private final String serviceName;

    OssServiceTypeEnum(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
