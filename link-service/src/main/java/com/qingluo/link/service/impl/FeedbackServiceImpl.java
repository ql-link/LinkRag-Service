package com.qingluo.link.service.impl;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.UserFeedbackMapper;
import com.qingluo.link.model.dto.entity.UserFeedback;
import com.qingluo.link.model.dto.request.CreateFeedbackRequest;
import com.qingluo.link.model.dto.response.FeedbackDTO;
import com.qingluo.link.model.enums.FeedbackStatus;
import com.qingluo.link.model.enums.FeedbackType;
import com.qingluo.link.service.FeedbackService;
import com.qingluo.link.service.OssApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    private static final int FEEDBACK_ERROR_CODE = 40020;
    private static final int DEFAULT_PRIORITY = 3;

    private final UserFeedbackMapper userFeedbackMapper;
    private final OssApplicationService ossApplicationService;
    private final IOssService ossService;

    @Override
    @Transactional
    public FeedbackDTO submit(CreateFeedbackRequest request, MultipartFile file) {
        validateCreateRequest(request);
        FeedbackType type = parseType(request.getType());
        String attachmentObjectKey = uploadAttachment(file);

        UserFeedback feedback = new UserFeedback();
        feedback.setType(type.name());
        feedback.setTitle(request.getTitle().trim());
        feedback.setContent(request.getContent().trim());
        feedback.setAttachmentObjectKey(attachmentObjectKey);
        feedback.setStatus(FeedbackStatus.PENDING.name());
        feedback.setPriority(DEFAULT_PRIORITY);

        try {
            userFeedbackMapper.insert(feedback);
        } catch (RuntimeException e) {
            cleanupUploadedAttachment(attachmentObjectKey);
            throw e;
        }
        return toDTO(feedback);
    }

    private void validateCreateRequest(CreateFeedbackRequest request) {
        if (request == null) {
            throw badRequest("反馈请求不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw badRequest("反馈标题不能为空");
        }
        if (request.getTitle().trim().length() > 128) {
            throw badRequest("反馈标题不能超过 128 个字符");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw badRequest("反馈内容不能为空");
        }
        if (request.getContent().trim().length() > 5000) {
            throw badRequest("反馈内容不能超过 5000 个字符");
        }
    }

    private FeedbackType parseType(String type) {
        try {
            return FeedbackType.of(type);
        } catch (IllegalArgumentException e) {
            throw badRequest("不支持的反馈类型");
        }
    }

    private String uploadAttachment(MultipartFile file) {
        if (file == null) {
            return null;
        }
        if (file.isEmpty()) {
            throw badRequest("反馈附件不能为空");
        }
        return ossApplicationService.upload("feedback", file);
    }

    private void cleanupUploadedAttachment(String attachmentObjectKey) {
        if (!StringUtils.hasText(attachmentObjectKey)) {
            return;
        }
        try {
            boolean deleted = ossService.deleteFile(OssSavePlaceEnum.PRIVATE, attachmentObjectKey);
            if (!deleted) {
                log.warn("删除反馈孤儿附件失败，objectKey={}", attachmentObjectKey);
            }
        } catch (RuntimeException ex) {
            log.warn("删除反馈孤儿附件时发生异常，objectKey={}", attachmentObjectKey, ex);
        }
    }

    private FeedbackDTO toDTO(UserFeedback feedback) {
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(feedback.getId());
        dto.setType(feedback.getType());
        dto.setTitle(feedback.getTitle());
        dto.setContent(feedback.getContent());
        dto.setAttachmentObjectKey(feedback.getAttachmentObjectKey());
        dto.setStatus(feedback.getStatus());
        dto.setPriority(feedback.getPriority());
        dto.setAdminId(feedback.getAdminId());
        dto.setAdminReply(feedback.getAdminReply());
        dto.setProcessedAt(feedback.getProcessedAt());
        dto.setCreatedAt(feedback.getCreatedAt());
        dto.setUpdatedAt(feedback.getUpdatedAt());
        return dto;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(FEEDBACK_ERROR_CODE, message, 400);
    }
}
