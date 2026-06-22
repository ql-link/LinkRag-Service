package com.qingluo.link.service;

import com.qingluo.link.service.oss.UploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface OssApplicationService {

    String upload(String bizType, MultipartFile file);

    /**
     * 与 {@link #upload} 相同的校验和上传逻辑，但同时返回对象 key 与 preview 值，
     * 使必须持久化 key 的调用方（如反馈）无需从公开 URL 反解 key。
     */
    UploadResult uploadAndDescribe(String bizType, MultipartFile file);

    /**
     * 与 {@link #uploadAndDescribe(String, MultipartFile)} 相同的校验和上传逻辑，
     * 但由调用方指定 object key。用于 object key 需要携带业务归属信息的场景。
     */
    UploadResult uploadAndDescribe(String bizType, MultipartFile file, String objectKey);
}
