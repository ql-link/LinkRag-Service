package com.qingluo.link.service.oss;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import java.util.Set;

public record OssUploadRule(
    OssSavePlaceEnum savePlace,
    Set<String> allowedFileSuffixes,
    long maxSizeBytes
) {
}
