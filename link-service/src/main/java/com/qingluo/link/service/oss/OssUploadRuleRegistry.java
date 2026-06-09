package com.qingluo.link.service.oss;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OssUploadRuleRegistry {

    public static final String ALL_SUFFIX_FLAG = "*";
    private static final long DEFAULT_MAX_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> IMAGE_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> FEEDBACK_SUFFIXES = Set.of(
        "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "txt", "md");

    private final Map<String, OssUploadRule> rules;

    public OssUploadRuleRegistry() {
        Map<String, OssUploadRule> allRules = new HashMap<>();
        allRules.put("avatar", new OssUploadRule(OssSavePlaceEnum.PUBLIC, IMAGE_SUFFIXES, DEFAULT_MAX_SIZE));
        allRules.put("chatImage", new OssUploadRule(OssSavePlaceEnum.PUBLIC, IMAGE_SUFFIXES, DEFAULT_MAX_SIZE));
        allRules.put("document", new OssUploadRule(
            OssSavePlaceEnum.PRIVATE, Set.of("pdf", "doc", "docx", "txt", "md"), 20 * 1024 * 1024L));
        allRules.put("cert", new OssUploadRule(
            OssSavePlaceEnum.PRIVATE, Collections.singleton(ALL_SUFFIX_FLAG), DEFAULT_MAX_SIZE));
        allRules.put("feedback", new OssUploadRule(
            OssSavePlaceEnum.PRIVATE, FEEDBACK_SUFFIXES, 10 * 1024 * 1024L));
        this.rules = Collections.unmodifiableMap(allRules);
    }

    public OssUploadRule getRule(String bizType) {
        return rules.get(bizType);
    }
}
