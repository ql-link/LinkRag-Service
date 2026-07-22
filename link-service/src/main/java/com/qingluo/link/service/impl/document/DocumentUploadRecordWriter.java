package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentUploadRecordWriter {

    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_FAILED = "failed";

    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final IOssService ossService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentOriginalFile prepareRecord(
            Long userId,
            Long datasetId,
            String originalFilename,
            String suffix,
            long fileSize,
            String contentType) {
        DocumentOriginalFile existing = documentOriginalFileMapper.selectOne(
            new LambdaQueryWrapper<DocumentOriginalFile>()
                .eq(DocumentOriginalFile::getUserId, userId)
                .eq(DocumentOriginalFile::getDatasetId, datasetId)
                .eq(DocumentOriginalFile::getOriginalFilename, originalFilename)
                .eq(DocumentOriginalFile::getFileSuffix, suffix));
        String bucket = ossService.getBucketName(OssSavePlaceEnum.RAW);
        if (existing != null) {
            if (!UPLOAD_FAILED.equals(existing.getUploadStatus())) {
                throw duplicate();
            }
            int updated = documentOriginalFileMapper.update(null,
                new LambdaUpdateWrapper<DocumentOriginalFile>()
                    .eq(DocumentOriginalFile::getId, existing.getId())
                    .eq(DocumentOriginalFile::getUploadStatus, UPLOAD_FAILED)
                    .set(DocumentOriginalFile::getUploadStatus, UPLOADING)
                    .set(DocumentOriginalFile::getIsUploadSuccess, false)
                    .set(DocumentOriginalFile::getFailureReason, null)
                    .set(DocumentOriginalFile::getObjectKey, null)
                    .set(DocumentOriginalFile::getFileUrl, null)
                    .set(DocumentOriginalFile::getFileSize, fileSize)
                    .set(DocumentOriginalFile::getContentType, contentType)
                    .set(DocumentOriginalFile::getBucketName, bucket));
            if (updated == 0) {
                throw duplicate();
            }
            existing.setUploadStatus(UPLOADING);
            existing.setIsUploadSuccess(false);
            existing.setFailureReason(null);
            existing.setObjectKey(null);
            existing.setFileUrl(null);
            existing.setFileSize(fileSize);
            existing.setContentType(contentType);
            existing.setBucketName(bucket);
            return existing;
        }

        DocumentOriginalFile record = new DocumentOriginalFile();
        record.setDatasetId(datasetId);
        record.setUserId(userId);
        record.setOriginalFilename(originalFilename);
        record.setFileSuffix(suffix);
        record.setFileSize(fileSize);
        record.setContentType(contentType);
        record.setBucketName(bucket);
        record.setUploadStatus(UPLOADING);
        record.setIsUploadSuccess(false);
        try {
            documentOriginalFileMapper.insert(record);
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
        return record;
    }

    private BusinessException duplicate() {
        return new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
    }
}
