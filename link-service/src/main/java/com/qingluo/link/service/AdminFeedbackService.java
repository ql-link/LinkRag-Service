package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.ReplyFeedbackRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackPriorityRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackStatusRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.dto.response.PageResult;

public interface AdminFeedbackService {

    PageResult<FeedbackDTO> list(int page, int pageSize, String status, String type);

    FeedbackDTO detail(Long id);

    FeedbackDTO updateStatus(Long id, UpdateFeedbackStatusRequest request);

    FeedbackDTO updatePriority(Long id, UpdateFeedbackPriorityRequest request);

    FeedbackDTO reply(Long adminId, Long id, ReplyFeedbackRequest request);
}
