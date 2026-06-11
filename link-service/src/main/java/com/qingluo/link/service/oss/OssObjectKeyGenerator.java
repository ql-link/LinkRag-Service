package com.qingluo.link.service.oss;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OssObjectKeyGenerator {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    public String generate(String bizType, String suffix) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if ("feedback".equals(bizType)) {
            return withOptionalSuffix("feedback/" + LocalDate.now().format(DATE_PATH_FORMATTER) + "/" + uuid, suffix);
        }
        return withOptionalSuffix(bizType + "/" + uuid, suffix);
    }

    private String withOptionalSuffix(String prefix, String suffix) {
        if (!StringUtils.hasText(suffix)) {
            return prefix;
        }
        return prefix + "." + suffix;
    }
}
