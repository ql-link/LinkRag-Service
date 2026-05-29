package com.qingluo.link.components.oss.service;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.io.File;
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
     * Uploads from an already-materialized local file. Used by asynchronous flows where the
     * request-scoped {@link MultipartFile} is no longer available off the request thread.
     * Returns a public preview URL for public files, or a relative object key for private files.
     */
    String upload2PreviewUrl(OssSavePlaceEnum ossSavePlaceEnum, File localFile, String contentType,
            String saveDirAndFileName);

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
