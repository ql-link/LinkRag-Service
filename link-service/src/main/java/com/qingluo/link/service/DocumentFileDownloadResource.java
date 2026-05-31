package com.qingluo.link.service;

import java.io.File;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentFileDownloadResource {

    private File file;
    private String originalFilename;
    private String contentType;
}
