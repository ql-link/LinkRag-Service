package com.qingluo.link.service.impl;

import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.service.OssApplicationService;
import com.qingluo.link.service.oss.OssObjectKeyGenerator;
import com.qingluo.link.service.oss.OssUploadRule;
import com.qingluo.link.service.oss.OssUploadRuleRegistry;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OssApplicationServiceImpl implements OssApplicationService {

    private static final int OSS_ERROR_CODE = 40001;

    private final IOssService ossService;
    private final OssUploadRuleRegistry ruleRegistry;
    private final OssObjectKeyGenerator objectKeyGenerator;

    public OssApplicationServiceImpl(
        IOssService ossService,
        OssUploadRuleRegistry ruleRegistry,
        OssObjectKeyGenerator objectKeyGenerator
    ) {
        this.ossService = ossService;
        this.ruleRegistry = ruleRegistry;
        this.objectKeyGenerator = objectKeyGenerator;
    }

    @Override
    public String upload(String bizType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要上传的文件");
        }

        OssUploadRule rule = ruleRegistry.getRule(bizType);
        if (rule == null) {
            throw badRequest("上传业务类型不支持");
        }

        String suffix = getFileSuffix(file.getOriginalFilename());
        if (!isAllowedSuffix(rule, suffix)) {
            throw badRequest("上传文件格式不支持");
        }

        if (file.getSize() > rule.maxSizeBytes()) {
            throw badRequest("上传大小请限制在 " + rule.maxSizeBytes() / 1024 / 1024 + "M 以内");
        }

        String objectKey = objectKeyGenerator.generate(bizType, suffix);
        String uploadResult = ossService.upload2PreviewUrl(rule.savePlace(), file, objectKey);
        if (!StringUtils.hasText(uploadResult)) {
            throw new BusinessException(50002, "文件上传失败", 500);
        }
        return uploadResult;
    }

    private boolean isAllowedSuffix(OssUploadRule rule, String suffix) {
        if (rule.allowedFileSuffixes().contains(OssUploadRuleRegistry.ALL_SUFFIX_FLAG)) {
            return true;
        }
        return StringUtils.hasText(suffix) && rule.allowedFileSuffixes().contains(suffix);
    }

    private String getFileSuffix(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(OSS_ERROR_CODE, message, 400);
    }
}
