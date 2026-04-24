package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.OssApplicationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/oss-files")
public class OssFileController {

    private final OssApplicationService ossApplicationService;

    public OssFileController(OssApplicationService ossApplicationService) {
        this.ossApplicationService = ossApplicationService;
    }

    @PostMapping("/{bizType}")
    public Result<String> singleFileUpload(
        @RequestParam("file") MultipartFile file,
        @PathVariable String bizType
    ) {
        return Result.success(ossApplicationService.upload(bizType, file));
    }
}
