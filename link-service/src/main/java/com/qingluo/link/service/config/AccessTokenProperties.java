package com.qingluo.link.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** Java 登录 access JWT 配置。私钥只允许通过运行环境提供。 */
@Data
@Component
@ConfigurationProperties(prefix = "tolink.auth.access-token")
public class AccessTokenProperties {

    /** 签发器装配开关；关闭后登录失败，不会回退签发旧 token。 */
    private boolean enabled = false;

    /** PKCS#8 PEM 私钥文件路径。 */
    private String privateKeyPath = "";

    private String issuer = "tolink-java";

    private List<String> audiences = List.of("tolink-java-api", "tolink-rag-api");

    /** 必须与 Sa-Token 当前登录态 timeout 一致。 */
    private long ttlSeconds = 7200L;
}
