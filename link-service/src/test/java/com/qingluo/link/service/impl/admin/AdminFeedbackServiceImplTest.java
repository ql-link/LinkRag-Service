package com.qingluo.link.service.impl.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.UserFeedbackMapper;
import com.qingluo.link.model.dto.entity.UserFeedback;
import com.qingluo.link.model.dto.request.ReplyFeedbackRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackPriorityRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackStatusRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.dto.response.PageResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackServiceImplTest {

    @Mock
    private UserFeedbackMapper userFeedbackMapper;

    @InjectMocks
    private AdminFeedbackServiceImpl adminFeedbackService;

    @Test
    @DisplayName("Should_ReturnFilteredPage_When_ListFeedback")
    void Should_ReturnFilteredPage_When_ListFeedback() {
        given(userFeedbackMapper.selectList(any())).willReturn(List.of(feedback(1L)));

        PageResult<FeedbackDTO> result = adminFeedbackService.list(1, 20, "PENDING", "BUG");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getType()).isEqualTo("BUG");
    }

    @Test
    @DisplayName("Should_SetProcessedAt_When_StatusBecomesResolved")
    void Should_SetProcessedAt_When_StatusBecomesResolved() {
        given(userFeedbackMapper.selectById(1L)).willReturn(feedback(1L));
        UpdateFeedbackStatusRequest request = new UpdateFeedbackStatusRequest();
        request.setStatus("RESOLVED");

        FeedbackDTO result = adminFeedbackService.updateStatus(1L, request);

        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        assertThat(result.getProcessedAt()).isNotNull();
        verify(userFeedbackMapper).updateById(any(UserFeedback.class));
    }

    @Test
    @DisplayName("Should_UpdatePriority_When_ValueIsValid")
    void Should_UpdatePriority_When_ValueIsValid() {
        given(userFeedbackMapper.selectById(1L)).willReturn(feedback(1L));
        UpdateFeedbackPriorityRequest request = new UpdateFeedbackPriorityRequest();
        request.setPriority(1);

        adminFeedbackService.updatePriority(1L, request);

        ArgumentCaptor<UserFeedback> captor = ArgumentCaptor.forClass(UserFeedback.class);
        verify(userFeedbackMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should_WriteReplyWithoutChangingStatus_When_AdminReplies")
    void Should_WriteReplyWithoutChangingStatus_When_AdminReplies() {
        UserFeedback feedback = feedback(1L);
        feedback.setStatus("PROCESSING");
        given(userFeedbackMapper.selectById(1L)).willReturn(feedback);
        ReplyFeedbackRequest request = new ReplyFeedbackRequest();
        request.setReply("We are checking this.");

        FeedbackDTO result = adminFeedbackService.reply(99L, 1L, request);

        assertThat(result.getAdminId()).isEqualTo(99L);
        assertThat(result.getAdminReply()).isEqualTo("We are checking this.");
        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        assertThat(result.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should_RejectInvalidStatus_When_UpdateStatus")
    void Should_RejectInvalidStatus_When_UpdateStatus() {
        UpdateFeedbackStatusRequest request = new UpdateFeedbackStatusRequest();
        request.setStatus("DONE");

        assertThatThrownBy(() -> adminFeedbackService.updateStatus(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("feedback status is not supported");
    }

    private UserFeedback feedback(Long id) {
        UserFeedback feedback = new UserFeedback();
        feedback.setId(id);
        feedback.setType("BUG");
        feedback.setTitle("Title");
        feedback.setContent("Content");
        feedback.setStatus("PENDING");
        feedback.setPriority(3);
        return feedback;
    }
}
