package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.UserFeedbackMapper;
import com.qingluo.link.model.dto.entity.UserFeedback;
import com.qingluo.link.model.dto.request.CreateFeedbackRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.service.OssApplicationService;
import com.qingluo.link.service.oss.UploadResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private UserFeedbackMapper userFeedbackMapper;

    @Mock
    private OssApplicationService ossApplicationService;

    @Mock
    private IOssService ossService;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    @Test
    @DisplayName("提交有效无附件反馈时写入待处理低优先级反馈")
    void Should_InsertPendingLowPriorityFeedback_When_SubmitValidWithoutFile() {
        given(userFeedbackMapper.insert(any())).willAnswer(invocation -> {
            UserFeedback feedback = invocation.getArgument(0);
            feedback.setId(10000L);
            return 1;
        });

        FeedbackDTO result = feedbackService.submit(request("BUG", "Bug title", "Bug detail"), null);

        assertThat(result.getId()).isEqualTo(10000L);
        assertThat(result.getType()).isEqualTo("BUG");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getPriority()).isEqualTo(3);
        assertThat(result.getAttachmentObjectKey()).isNull();
        verify(ossApplicationService, never()).uploadAndDescribe(any(), any());
    }

    @Test
    @DisplayName("提交带附件反馈时只保存 objectKey 而非公开 URL（方案甲）")
    void Should_StoreObjectKeyNotUrl_When_FileProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", "png".getBytes());
        // 公开桶上传返回完整 URL，但方案甲要求实体只存 objectKey
        given(ossApplicationService.uploadAndDescribe("feedback", file))
            .willReturn(new UploadResult(
                "feedback/2026/06/abc.png",
                "http://minio:9000/tolink-public/feedback/2026/06/abc.png"));

        feedbackService.submit(request(null, "Feature", "Please add this"), file);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackMapper).insert(captor.capture());
        String stored = captor.getValue().getAttachmentObjectKey();
        assertThat(captor.getValue().getType()).isEqualTo("OTHER");
        assertThat(stored).isEqualTo("feedback/2026/06/abc.png");
        assertThat(stored).doesNotStartWith("http");
        assertThat(stored).doesNotContain("tolink-public");
    }

    @Test
    @DisplayName("提交不支持的反馈类型时拒绝请求")
    void Should_RejectUnsupportedType_When_Submit() {
        assertThatThrownBy(() -> feedbackService.submit(request("INVALID", "Title", "Content"), null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("不支持的反馈类型");

        verify(userFeedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("数据库写入失败时删除已上传的反馈附件")
    void Should_DeleteUploadedAttachment_When_DbInsertFails() {
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", "png".getBytes());
        given(ossApplicationService.uploadAndDescribe("feedback", file))
            .willReturn(new UploadResult(
                "feedback/2026/06/orphan.png",
                "http://minio:9000/tolink-public/feedback/2026/06/orphan.png"));
        willThrow(new RuntimeException("db down")).given(userFeedbackMapper).insert(any());

        assertThatThrownBy(() -> feedbackService.submit(request("BUG", "Title", "Content"), file))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("db down");

        // 公开桶清理：用 objectKey 而非 URL，避免定位失败
        verify(ossService).deleteFile(OssSavePlaceEnum.PUBLIC, "feedback/2026/06/orphan.png");
    }

    private CreateFeedbackRequest request(String type, String title, String content) {
        CreateFeedbackRequest request = new CreateFeedbackRequest();
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        return request;
    }
}
