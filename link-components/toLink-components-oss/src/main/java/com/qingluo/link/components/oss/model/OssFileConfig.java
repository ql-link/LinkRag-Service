package com.qingluo.link.components.oss.model;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Centralized business upload rules.
 */
public class OssFileConfig {

    public static final String ALL_SUFFIX_FLAG = "*";
    public static final long ALL_MAX_SIZE = -1L;
    public static final long DEFAULT_MAX_SIZE = 5 * 1024 * 1024L;
    public static final Set<String> IMG_SUFFIX = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final Map<String, OssFileConfig> ALL_BIZ_TYPE_MAP = new HashMap<>();

    static {
        ALL_BIZ_TYPE_MAP.put(BizType.AVATAR,
                new OssFileConfig(OssSavePlaceEnum.PUBLIC, IMG_SUFFIX, DEFAULT_MAX_SIZE));
        ALL_BIZ_TYPE_MAP.put(BizType.CHAT_IMAGE,
                new OssFileConfig(OssSavePlaceEnum.PUBLIC, IMG_SUFFIX, DEFAULT_MAX_SIZE));
        ALL_BIZ_TYPE_MAP.put(BizType.DOCUMENT,
                new OssFileConfig(OssSavePlaceEnum.PRIVATE, Set.of("pdf", "doc", "docx", "txt", "md"), 20 * 1024 * 1024L));
        ALL_BIZ_TYPE_MAP.put(BizType.CERT,
                new OssFileConfig(OssSavePlaceEnum.PRIVATE, Collections.singleton(ALL_SUFFIX_FLAG), DEFAULT_MAX_SIZE));
    }

    private final OssSavePlaceEnum ossSavePlaceEnum;
    private final Set<String> allowFileSuffix;
    private final Long maxSize;

    public OssFileConfig(OssSavePlaceEnum ossSavePlaceEnum, Set<String> allowFileSuffix, Long maxSize) {
        this.ossSavePlaceEnum = ossSavePlaceEnum;
        this.allowFileSuffix = allowFileSuffix;
        this.maxSize = maxSize;
    }

    public static OssFileConfig getOssFileConfigByBizType(String bizType) {
        return ALL_BIZ_TYPE_MAP.get(bizType);
    }

    public static Map<String, OssFileConfig> getAllBizTypeMap() {
        return Collections.unmodifiableMap(ALL_BIZ_TYPE_MAP);
    }

    public boolean isAllowFileSuffix(String suffix) {
        if (allowFileSuffix.contains(ALL_SUFFIX_FLAG)) {
            return true;
        }
        return StringUtils.hasText(suffix) && allowFileSuffix.contains(suffix.toLowerCase(Locale.ROOT));
    }

    public boolean isMaxSizeLimit(Long fileSize) {
        if (ALL_MAX_SIZE == maxSize) {
            return true;
        }
        return maxSize >= (fileSize == null ? 0L : fileSize);
    }

    public OssSavePlaceEnum getOssSavePlaceEnum() {
        return ossSavePlaceEnum;
    }

    public Set<String> getAllowFileSuffix() {
        return allowFileSuffix;
    }

    public Long getMaxSize() {
        return maxSize;
    }

    public interface BizType {
        String AVATAR = "avatar";
        String CHAT_IMAGE = "chatImage";
        String DOCUMENT = "document";
        String CERT = "cert";
    }
}
