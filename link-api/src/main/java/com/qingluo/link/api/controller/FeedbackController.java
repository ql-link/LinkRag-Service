package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.CreateFeedbackRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FeedbackDTO> submit(@RequestParam(required = false) String type,
                                      @RequestParam String title,
                                      @RequestParam String content,
                                      @RequestParam(required = false) MultipartFile file) {
        CreateFeedbackRequest request = new CreateFeedbackRequest();
        request.setType(type);
        request.setTitle(title);
        request.setContent(content);
        return Result.success(feedbackService.submit(request, file));
    }
}
