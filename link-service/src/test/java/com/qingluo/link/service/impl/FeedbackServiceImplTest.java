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
        verify(ossApplicationService, never()).upload(any(), any());
    }

    @Test
    @DisplayName("提交带附件反馈时上传附件并保存对象 key")
    void Should_UploadAttachmentAndStoreObjectKey_When_FileProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", "png".getBytes());
        given(ossApplicationService.upload("feedback", file))
            .willReturn("feedback/2026/06/09/abc.png");

        feedbackService.submit(request(null, "Feature", "Please add this"), file);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("OTHER");
        assertThat(captor.getValue().getAttachmentObjectKey()).isEqualTo("feedback/2026/06/09/abc.png");
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
        given(ossApplicationService.upload("feedback", file))
            .willReturn("feedback/2026/06/09/orphan.png");
        willThrow(new RuntimeException("db down")).given(userFeedbackMapper).insert(any());

        assertThatThrownBy(() -> feedbackService.submit(request("BUG", "Title", "Content"), file))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("db down");

        verify(ossService).deleteFile(OssSavePlaceEnum.PRIVATE, "feedback/2026/06/09/orphan.png");
    }

    private CreateFeedbackRequest request(String type, String title, String content) {
        CreateFeedbackRequest request = new CreateFeedbackRequest();
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        return request;
    }
}
