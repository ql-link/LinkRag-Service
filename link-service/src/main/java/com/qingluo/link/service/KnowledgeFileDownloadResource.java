package com.qingluo.link.service;

import java.io.File;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeFileDownloadResource {

    private File file;
    private String originalFilename;
    private String contentType;
}
