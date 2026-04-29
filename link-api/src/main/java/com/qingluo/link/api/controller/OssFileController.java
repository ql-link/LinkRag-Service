package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.OssApplicationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用 OSS 文件上传入口。
 *
 * <p>该接口服务于头像、图片等通用业务附件上传；知识库原文件上传走
 * {@link KnowledgeFileController}，以便独立维护原文件表、失败状态和解析流程。
 */
@RestController
@RequestMapping("/api/v1/oss-files")
public class OssFileController {

    private final OssApplicationService ossApplicationService;

    /**
     * 创建通用 OSS 文件上传 Controller。
     *
     * @param ossApplicationService OSS 应用服务，负责按业务类型选择存储路径和访问地址
     */
    public OssFileController(OssApplicationService ossApplicationService) {
        this.ossApplicationService = ossApplicationService;
    }

    /**
     * 上传单个通用业务文件。
     *
     * <p>bizType 决定文件归属业务和存储规则；本接口不承担知识库原文件状态流转职责。
     *
     * @param file 待上传文件
     * @param bizType 业务文件类型
     * @return 上传后的可访问地址或对象标识
     */
    @PostMapping("/{bizType}")
    public Result<String> singleFileUpload(
        @RequestParam("file") MultipartFile file,
        @PathVariable String bizType
    ) {
        return Result.success(ossApplicationService.upload(bizType, file));
    }
}
