package com.qingluo.link.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssApplicationService {

    String upload(String bizType, MultipartFile file);
}
