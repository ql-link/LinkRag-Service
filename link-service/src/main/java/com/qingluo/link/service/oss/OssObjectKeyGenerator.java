package com.qingluo.link.service.oss;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OssObjectKeyGenerator {

    public String generate(String bizType, String suffix) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(suffix)) {
            return bizType + "/" + uuid;
        }
        return bizType + "/" + uuid + "." + suffix;
    }
}
