package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.CreateFeedbackRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import org.springframework.web.multipart.MultipartFile;

public interface FeedbackService {

    FeedbackDTO submit(CreateFeedbackRequest request, MultipartFile file);
}
