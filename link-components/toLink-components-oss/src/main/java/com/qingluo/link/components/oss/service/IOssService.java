package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import org.springframework.web.multipart.MultipartFile;

/**
 * Provider-neutral object storage service.
 */
public interface IOssService {

    /**
     * Uploads a file and returns a public preview URL for public files,
     * or a relative object key for private files.
     */
    String upload2PreviewUrl(OssSavePlaceEnum ossSavePlaceEnum, MultipartFile multipartFile, String saveDirAndFileName);

    /**
     * Downloads a stored object into a local target path.
     */
    boolean downloadFile(OssSavePlaceEnum ossSavePlaceEnum, String source, String target);

    /**
     * Deletes a stored object immediately.
     */
    boolean deleteFile(OssSavePlaceEnum ossSavePlaceEnum, String objectKey);

    /**
     * Resolves the physical or logical bucket/container name for the given place.
     */
    String getBucketName(OssSavePlaceEnum ossSavePlaceEnum);
}
