package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.ReplyFeedbackRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackPriorityRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackStatusRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.AdminFeedbackService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    @GetMapping
    public Result<PageResult<FeedbackDTO>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String type) {
        return Result.success(adminFeedbackService.list(page, pageSize, status, type));
    }

    @GetMapping("/{id}")
    public Result<FeedbackDTO> detail(@PathVariable Long id) {
        return Result.success(adminFeedbackService.detail(id));
    }

    @PatchMapping("/{id}/status")
    public Result<FeedbackDTO> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateFeedbackStatusRequest request) {
        return Result.success(adminFeedbackService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/priority")
    public Result<FeedbackDTO> updatePriority(@PathVariable Long id,
                                              @Valid @RequestBody UpdateFeedbackPriorityRequest request) {
        return Result.success(adminFeedbackService.updatePriority(id, request));
    }

    @PatchMapping("/{id}/reply")
    public Result<FeedbackDTO> reply(@PathVariable Long id,
                                     @Valid @RequestBody ReplyFeedbackRequest request) {
        Long adminId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(adminFeedbackService.reply(adminId, id, request));
    }
}
