package com.qingluo.link.service.document;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record DocumentFileUploadCommand(
    MultipartFile file,
    boolean parseImmediately,
    String matchMode,
    String documentPath,
    List<MultipartFile> assets,
    List<String> assetRelativePaths,
    List<String> assetInventoryPaths
) {
    public DocumentFileUploadCommand {
        assets = assets == null ? List.of() : List.copyOf(assets);
        assetRelativePaths = assetRelativePaths == null ? List.of() : List.copyOf(assetRelativePaths);
        assetInventoryPaths = assetInventoryPaths == null ? List.of() : List.copyOf(assetInventoryPaths);
    }
}
