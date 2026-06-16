package com.qingluo.link.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.UserFeedbackMapper;
import com.qingluo.link.model.dto.entity.UserFeedback;
import com.qingluo.link.model.dto.request.ReplyFeedbackRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackPriorityRequest;
import com.qingluo.link.model.dto.request.UpdateFeedbackStatusRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.FeedbackStatus;
import com.qingluo.link.model.enums.FeedbackType;
import com.qingluo.link.service.AdminFeedbackService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    private static final int FEEDBACK_ERROR_CODE = 40020;

    private final UserFeedbackMapper userFeedbackMapper;
    private final IOssService ossService;

    @Override
    public PageResult<FeedbackDTO> list(int page, int pageSize, String status, String type) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<UserFeedback>()
            .orderByDesc(UserFeedback::getCreatedAt)
            .orderByDesc(UserFeedback::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(UserFeedback::getStatus, parseStatus(status).name());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(UserFeedback::getType, parseType(type).name());
        }

        PageHelper.startPage(page, pageSize);
        var records = userFeedbackMapper.selectList(wrapper);
        PageInfo<UserFeedback> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records.stream().map(this::toDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public FeedbackDTO detail(Long id) {
        return toDTO(getFeedback(id));
    }

    @Override
    @Transactional
    public FeedbackDTO updateStatus(Long id, UpdateFeedbackStatusRequest request) {
        if (request == null) {
            throw badRequest("反馈状态不能为空");
        }
        FeedbackStatus status = parseStatus(request.getStatus());
        UserFeedback feedback = getFeedback(id);
        feedback.setStatus(status.name());
        if (status.isTerminal()) {
            feedback.setProcessedAt(LocalDateTime.now());
        }
        userFeedbackMapper.updateById(feedback);
        return toDTO(feedback);
    }

    @Override
    @Transactional
    public FeedbackDTO updatePriority(Long id, UpdateFeedbackPriorityRequest request) {
        if (request == null || request.getPriority() == null) {
            throw badRequest("反馈优先级不能为空");
        }
        if (request.getPriority() < 1 || request.getPriority() > 3) {
            throw badRequest("反馈优先级必须在 1 到 3 之间");
        }
        UserFeedback feedback = getFeedback(id);
        feedback.setPriority(request.getPriority());
        userFeedbackMapper.updateById(feedback);
        return toDTO(feedback);
    }

    @Override
    @Transactional
    public FeedbackDTO reply(Long adminId, Long id, ReplyFeedbackRequest request) {
        if (adminId == null) {
            throw badRequest("管理员 ID 不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getReply())) {
            throw badRequest("管理员回复不能为空");
        }
        if (request.getReply().trim().length() > 5000) {
            throw badRequest("管理员回复不能超过 5000 个字符");
        }
        UserFeedback feedback = getFeedback(id);
        feedback.setAdminId(adminId);
        feedback.setAdminReply(request.getReply().trim());
        feedback.setProcessedAt(LocalDateTime.now());
        userFeedbackMapper.updateById(feedback);
        return toDTO(feedback);
    }

    private UserFeedback getFeedback(Long id) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(404, "反馈不存在", 404);
        }
        return feedback;
    }

    private FeedbackStatus parseStatus(String status) {
        try {
            return FeedbackStatus.of(status);
        } catch (IllegalArgumentException e) {
            throw badRequest("不支持的反馈状态");
        }
    }

    private FeedbackType parseType(String type) {
        try {
            return FeedbackType.of(type);
        } catch (IllegalArgumentException e) {
            throw badRequest("不支持的反馈类型");
        }
    }

    private FeedbackDTO toDTO(UserFeedback feedback) {
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(feedback.getId());
        dto.setType(feedback.getType());
        dto.setTitle(feedback.getTitle());
        dto.setContent(feedback.getContent());
        dto.setAttachmentObjectKey(feedback.getAttachmentObjectKey());
        dto.setAttachmentUrl(resolveAttachmentUrl(feedback.getAttachmentObjectKey()));
        dto.setStatus(feedback.getStatus());
        dto.setPriority(feedback.getPriority());
        dto.setAdminId(feedback.getAdminId());
        dto.setAdminReply(feedback.getAdminReply());
        dto.setProcessedAt(feedback.getProcessedAt());
        dto.setCreatedAt(feedback.getCreatedAt());
        dto.setUpdatedAt(feedback.getUpdatedAt());
        return dto;
    }

    private String resolveAttachmentUrl(String attachmentObjectKey) {
        if (!StringUtils.hasText(attachmentObjectKey)) {
            return null;
        }
        return ossService.resolvePublicUrl(OssSavePlaceEnum.PUBLIC, attachmentObjectKey);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(FEEDBACK_ERROR_CODE, message, 400);
    }
}
