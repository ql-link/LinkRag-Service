package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.service.oss.OssObjectKeyGenerator;
import com.qingluo.link.service.oss.OssUploadRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class OssApplicationServiceImplTest {

    private RecordingOssService ossService;
    private OssApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        ossService = new RecordingOssService();
        service = new OssApplicationServiceImpl(
            ossService,
            new OssUploadRuleRegistry(),
            new OssObjectKeyGenerator()
        );
    }

    @Test
    void Should_Return_Public_Preview_Url_When_Uploading_Avatar_File() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "hello".getBytes());

        String result = service.upload("avatar", file);

        assertThat(result).startsWith("/preview/avatar/");
        assertThat(ossService.lastSavePlace).isEqualTo(OssSavePlaceEnum.PUBLIC);
        assertThat(ossService.lastObjectKey).startsWith("avatar/");
        assertThat(ossService.lastObjectKey).endsWith(".png");
    }

    @Test
    void Should_Return_Private_Object_Key_When_Uploading_Cert_File() {
        MockMultipartFile file = new MockMultipartFile("file", "cert.pem", "text/plain", "secret".getBytes());

        String result = service.upload("cert", file);

        assertThat(result).startsWith("cert/");
        assertThat(result).endsWith(".pem");
        assertThat(ossService.lastSavePlace).isEqualTo(OssSavePlaceEnum.PRIVATE);
    }

    @Test
    void Should_Return_Public_Url_And_Monthly_Object_Key_When_Uploading_Feedback_File() {
        MockMultipartFile file = new MockMultipartFile("file", "feedback.png", "image/png", "hello".getBytes());

        com.qingluo.link.service.oss.UploadResult result = service.uploadAndDescribe("feedback", file);

        // 路径精确到月、不含日：四段 feedback/yyyy/MM/uuid，且不匹配五段 feedback/yyyy/MM/dd
        assertThat(result.objectKey()).matches("feedback/\\d{4}/\\d{2}/[a-f0-9]{32}\\.png");
        assertThat(result.objectKey()).doesNotMatch("feedback/\\d{4}/\\d{2}/\\d{2}/.*");
        assertThat(ossService.lastSavePlace).isEqualTo(OssSavePlaceEnum.PUBLIC);
        assertThat(result.previewUrl()).startsWith("/preview/feedback/");
    }

    @Test
    void Should_Reject_File_When_BizType_Is_Unknown() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.upload("unknown", file))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("上传业务类型不支持");
    }

    @Test
    void Should_Reject_File_When_Suffix_Is_Not_Allowed() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.upload("avatar", file))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("上传文件格式不支持");
    }

    @Test
    void Should_Reject_File_When_Size_Exceeds_Rule_Limit() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "avatar.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload("avatar", file))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("上传大小请限制在 5M 以内");
    }

    private static class RecordingOssService implements IOssService {

        private OssSavePlaceEnum lastSavePlace;
        private String lastObjectKey;

        @Override
        public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, MultipartFile multipartFile, String saveDirAndFileName) {
            this.lastSavePlace = ossSavePlaceEnum;
            this.lastObjectKey = saveDirAndFileName;
            if (ossSavePlaceEnum == OssSavePlaceEnum.PUBLIC) {
                return "/preview/" + saveDirAndFileName;
            }
            return saveDirAndFileName;
        }

        @Override
        public String upload2PreviewUrl(
            OssSavePlaceEnum ossSavePlaceEnum, java.io.File localFile, String contentType, String saveDirAndFileName) {
            this.lastSavePlace = ossSavePlaceEnum;
            this.lastObjectKey = saveDirAndFileName;
            if (ossSavePlaceEnum == OssSavePlaceEnum.PUBLIC) {
                return "/preview/" + saveDirAndFileName;
            }
            return saveDirAndFileName;
        }

        @Override
        public boolean downloadFile(OssSavePlaceEnum ossSavePlaceEnum, String source, String target) {
            return false;
        }

        @Override
        public boolean deleteFile(OssSavePlaceEnum ossSavePlaceEnum, String objectKey) {
            return false;
        }

        @Override
        public String getBucketName(OssSavePlaceEnum ossSavePlaceEnum) {
            return ossSavePlaceEnum == OssSavePlaceEnum.PUBLIC ? "public-bucket" : "private-bucket";
        }

        @Override
        public String resolvePublicUrl(OssSavePlaceEnum ossSavePlaceEnum, String objectKey) {
            return "/preview/" + objectKey;
        }
    }
}
