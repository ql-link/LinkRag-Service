package com.qingluo.link.components.oss.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * OSS component configuration.
 */
@ConfigurationProperties(prefix = OssProperties.PREFIX)
public class OssProperties {

    public static final String PREFIX = "tolink.oss";
    public static final String SERVICE_TYPE_PROPERTY = PREFIX + ".service-type";

    /**
     * Active provider, for example local, aliyun-oss, minio, s3 or cos.
     */
    private String serviceType = "local";

    /**
     * Root local directory. Public and private paths default under this path.
     */
    private String fileRootPath = Paths.get(System.getProperty("user.home"), ".tolink", "oss").toString();

    /**
     * Local directory for public files.
     */
    private String filePublicPath;

    /**
     * Local directory for private files.
     */
    private String filePrivatePath;

    /**
     * URL prefix used by local public uploads.
     */
    private String publicBaseUrl;

    private final AliyunOss aliyunOss = new AliyunOss();
    private final Minio minio = new Minio();

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getFileRootPath() {
        return fileRootPath;
    }

    public void setFileRootPath(String fileRootPath) {
        this.fileRootPath = fileRootPath;
    }

    public String getFilePublicPath() {
        if (StringUtils.hasText(filePublicPath)) {
            return filePublicPath;
        }
        return Path.of(fileRootPath, "public").toString();
    }

    public void setFilePublicPath(String filePublicPath) {
        this.filePublicPath = filePublicPath;
    }

    public String getFilePrivatePath() {
        if (StringUtils.hasText(filePrivatePath)) {
            return filePrivatePath;
        }
        return Path.of(fileRootPath, "private").toString();
    }

    public void setFilePrivatePath(String filePrivatePath) {
        this.filePrivatePath = filePrivatePath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public AliyunOss getAliyunOss() {
        return aliyunOss;
    }

    public Minio getMinio() {
        return minio;
    }

    public static class AliyunOss {

        private String endpoint;
        private String publicBucketName;
        private String privateBucketName;
        private String accessKeyId;
        private String accessKeySecret;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicBucketName() {
            return publicBucketName;
        }

        public void setPublicBucketName(String publicBucketName) {
            this.publicBucketName = publicBucketName;
        }

        public String getPrivateBucketName() {
            return privateBucketName;
        }

        public void setPrivateBucketName(String privateBucketName) {
            this.privateBucketName = privateBucketName;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }
    }

    public static class Minio {

        private String endpoint;
        private String publicBucketName;
        private String privateBucketName;
        private String blogBucketName;
        private String accessKey;
        private String secretKey;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicBucketName() {
            return publicBucketName;
        }

        public void setPublicBucketName(String publicBucketName) {
            this.publicBucketName = publicBucketName;
        }

        public String getPrivateBucketName() {
            return privateBucketName;
        }

        public void setPrivateBucketName(String privateBucketName) {
            this.privateBucketName = privateBucketName;
        }

        public String getBlogBucketName() {
            return blogBucketName;
        }

        public void setBlogBucketName(String blogBucketName) {
            this.blogBucketName = blogBucketName;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
