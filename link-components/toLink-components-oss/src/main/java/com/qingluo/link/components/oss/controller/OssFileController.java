package com.qingluo.link.components.oss.controller;

import com.qingluo.link.components.oss.model.OssFileConfig;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.response.Result;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unified OSS file upload API.
 */
@RestController
@RequestMapping("/api/v1/oss-files")
public class OssFileController {

    private static final int OSS_ERROR_CODE = 40001;

    private final IOssService ossService;

    public OssFileController(IOssService ossService) {
        this.ossService = ossService;
    }

    @PostMapping("/{bizType}")
    public Result<String> singleFileUpload(
            @RequestParam("file") MultipartFile file,
            @PathVariable String bizType) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要上传的文件");
        }

        OssFileConfig config = OssFileConfig.getOssFileConfigByBizType(bizType);
        if (config == null) {
            throw badRequest("上传业务类型不支持");
        }

        String suffix = getFileSuffix(file.getOriginalFilename());
        if (!config.isAllowFileSuffix(suffix)) {
            throw badRequest("上传文件格式不支持");
        }

        if (!config.isMaxSizeLimit(file.getSize())) {
            throw badRequest("上传大小请限制在 " + config.getMaxSize() / 1024 / 1024 + "M 以内");
        }

        String objectKey = buildObjectKey(bizType, suffix);
        String uploadResult = ossService.upload2PreviewUrl(config.getOssSavePlaceEnum(), file, objectKey);
        if (!StringUtils.hasText(uploadResult)) {
            throw new BusinessException(50002, "文件上传失败", 500);
        }
        return Result.success(uploadResult);
    }

    private String buildObjectKey(String bizType, String suffix) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(suffix)) {
            return bizType + "/" + uuid;
        }
        return bizType + "/" + uuid + "." + suffix;
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
