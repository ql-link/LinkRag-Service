package com.qingluo.link.service.impl.know;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.service.DocumentParseTaskService;
import com.qingluo.link.service.mq.DocumentParseTaskMQ;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 解析任务编排服务。Java 仅管理最新任务指针和消息投递，日志终态由 Python 写入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParseTaskServiceImpl implements DocumentParseTaskService {

    public static final String TASK_CREATED = "created";
    public static final String TASK_SUCCESS = "success";
    public static final String TASK_FAILED = "failed";
    public static final String TRIGGER_UPLOAD_AUTO = "upload_auto";
    public static final String TRIGGER_MANUAL_RETRY = "manual_retry";

    private static final String UPLOAD_SUCCESS = "success";
    private static final String MD_BUCKET = "rag-md";

    private final DatasetMapper datasetMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentParseFileMapper documentParseFileMapper;
    private final DocumentParsedLogMapper documentParsedLogMapper;
    private final ObjectProvider<MQSend> mqSendProvider;

    @Override
    @Transactional
    public FileParseSubmitDTO submitManualParse(Long userId, Long fileId) {
        DocumentOriginalFile file = getOwnedUploadedFile(userId, fileId);
        DocumentParseFile parseFile = requireParseFile(fileId);
        if (hasRunningTask(parseFile, fileId)) {
            throw new BusinessException(409, "文件正在解析中，请勿重复提交", 409);
        }
        submit(file, parseFile, TRIGGER_MANUAL_RETRY);
        return buildSubmitDTO(file);
    }

    @Override
    @Transactional
    public void submitAutoParseAfterUpload(Long userId, DocumentOriginalFile originalFile) {
        if (originalFile == null || originalFile.getId() == null) {
            return;
        }
        DocumentOriginalFile file = getOwnedUploadedFile(userId, originalFile.getId());
        DocumentParseFile parseFile = requireParseFile(file.getId());
        if (hasRunningTask(parseFile, file.getId())) {
            log.info("Skip duplicate automatic parse, fileId={}, latestTaskId={}",
                file.getId(), parseFile.getLatestParseTaskId());
            return;
        }
        submit(file, parseFile, TRIGGER_UPLOAD_AUTO);
    }

    @Override
    public List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(400, "请选择要查看的文件", 400);
        }
        assertOwnedDataset(userId, datasetId);
        List<Long> ids = fileIds.stream().distinct().toList();
        Map<Long, DocumentOriginalFile> files = loadOwnedFiles(userId, datasetId, ids);
        Map<Long, DocumentParseFile> parseFiles = loadParseFiles(ids);
        Map<Long, DocumentParsedLog> currentLogs = resolveCurrentLogs(ids, parseFiles);
        List<FileParseResultDTO> result = new ArrayList<>(ids.size());
        for (Long fileId : ids) {
            result.add(buildResultDTO(files.get(fileId), parseFiles.get(fileId), currentLogs.get(fileId)));
        }
        return result;
    }

    private void submit(DocumentOriginalFile file, DocumentParseFile parseFile, String triggerMode) {
        String taskId = UUID.randomUUID().toString();
        documentParseFileMapper.update(null, new LambdaUpdateWrapper<DocumentParseFile>()
            .eq(DocumentParseFile::getId, parseFile.getId())
            .set(DocumentParseFile::getLatestParseTaskId, taskId));
        try {
            MQSend sender = mqSendProvider.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("MQ sender is not configured");
            }
            sender.send(new DocumentParseTaskMQ(buildPayload(taskId, triggerMode, parseFile, file)));
        } catch (RuntimeException e) {
            log.error("Submit parse task failed, fileId={}, taskId={}", file.getId(), taskId, e);
            throw new BusinessException(500, "解析提交失败，请稍后重试", 500);
        }
    }

    private DocumentParseTaskMQ.MsgPayload buildPayload(String taskId, String triggerMode,
                                                          DocumentParseFile parseFile,
                                                          DocumentOriginalFile file) {
        return new DocumentParseTaskMQ.MsgPayload(
            taskId,
            file.getId(),
            parseFile.getId(),
            file.getUserId(),
            file.getDatasetId(),
            triggerMode,
            file.getFileSuffix(),
            file.getBucketName(),
            file.getObjectKey(),
            file.getOriginalFilename(),
            MD_BUCKET,
            buildMdObjectKey(file, taskId)
        );
    }

    private String buildMdObjectKey(DocumentOriginalFile file, String taskId) {
        LocalDate date = LocalDate.now();
        return "parsed/user-%d/dataset-%d/%04d/%02d/%02d/%s/%s".formatted(
            file.getUserId(), file.getDatasetId(), date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
            taskId, toMdFilename(file.getOriginalFilename()));
    }

    private String toMdFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + ".md";
    }

    /**
     * 指针先于 Python 日志到达时也必须视为运行中，避免重复消息覆盖最新任务。
     */
    private boolean hasRunningTask(DocumentParseFile parseFile, Long fileId) {
        if (StringUtils.hasText(parseFile.getLatestParseTaskId())) {
            DocumentParsedLog latest = findLogByTaskId(parseFile.getLatestParseTaskId());
            if (latest == null || TASK_CREATED.equals(latest.getTaskStatus())) {
                return true;
            }
        }
        return documentParsedLogMapper.selectCount(new LambdaQueryWrapper<DocumentParsedLog>()
            .eq(DocumentParsedLog::getDocumentOriginalFileId, fileId)
            .eq(DocumentParsedLog::getTaskStatus, TASK_CREATED)) > 0;
    }

    private Map<Long, DocumentOriginalFile> loadOwnedFiles(Long userId, Long datasetId, List<Long> fileIds) {
        Map<Long, DocumentOriginalFile> result = documentOriginalFileMapper.selectList(
                new LambdaQueryWrapper<DocumentOriginalFile>()
                    .eq(DocumentOriginalFile::getUserId, userId)
                    .eq(DocumentOriginalFile::getDatasetId, datasetId)
                    .in(DocumentOriginalFile::getId, fileIds))
            .stream().collect(Collectors.toMap(DocumentOriginalFile::getId, Function.identity()));
        if (result.size() != fileIds.size()) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        return result;
    }

    private Map<Long, DocumentParseFile> loadParseFiles(List<Long> fileIds) {
        return documentParseFileMapper.selectList(new LambdaQueryWrapper<DocumentParseFile>()
                .in(DocumentParseFile::getDocumentOriginalFileId, fileIds))
            .stream().collect(Collectors.toMap(DocumentParseFile::getDocumentOriginalFileId, Function.identity()));
    }

    private Map<Long, DocumentParsedLog> resolveCurrentLogs(List<Long> fileIds,
                                                             Map<Long, DocumentParseFile> parseFiles) {
        List<DocumentParsedLog> logs = documentParsedLogMapper.selectList(new LambdaQueryWrapper<DocumentParsedLog>()
            .in(DocumentParsedLog::getDocumentOriginalFileId, fileIds)
            .orderByDesc(DocumentParsedLog::getCreatedAt)
            .orderByDesc(DocumentParsedLog::getId));
        Map<String, DocumentParsedLog> byTaskId = logs.stream()
            .collect(Collectors.toMap(DocumentParsedLog::getTaskId, Function.identity(), (first, ignored) -> first));
        Map<Long, DocumentParsedLog> result = new HashMap<>();
        for (Long fileId : fileIds) {
            DocumentParseFile parseFile = parseFiles.get(fileId);
            if (parseFile != null && StringUtils.hasText(parseFile.getLatestParseTaskId())) {
                DocumentParsedLog log = byTaskId.get(parseFile.getLatestParseTaskId());
                if (log != null) {
                    result.put(fileId, log);
                }
            }
        }
        return result;
    }

    private FileParseResultDTO buildResultDTO(DocumentOriginalFile file, DocumentParseFile parseFile,
                                               DocumentParsedLog log) {
        FileParseResultDTO dto = new FileParseResultDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setParsedFilename(log != null && StringUtils.hasText(log.getParsedFilename())
            ? log.getParsedFilename() : toMdFilename(file.getOriginalFilename()));
        if (log == null) {
            dto.setParseStatus(parseFile != null && StringUtils.hasText(parseFile.getLatestParseTaskId())
                ? TASK_CREATED : null);
        } else {
            dto.setParseStatus(log.getTaskStatus());
            dto.setFailureReason(log.getFailureReason());
        }
        dto.setFrontendStatus(frontendStatus(dto.getParseStatus()));
        return dto;
    }

    private String frontendStatus(String status) {
        if (TASK_CREATED.equals(status)) {
            return "parsing";
        }
        if (TASK_SUCCESS.equals(status)) {
            return "parse_success";
        }
        if (TASK_FAILED.equals(status)) {
            return "parse_failed";
        }
        return "parse_waiting";
    }

    private FileParseSubmitDTO buildSubmitDTO(DocumentOriginalFile file) {
        FileParseSubmitDTO dto = new FileParseSubmitDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setFrontendStatus("parsing");
        return dto;
    }

    private DocumentParsedLog findLogByTaskId(String taskId) {
        return documentParsedLogMapper.selectOne(new LambdaQueryWrapper<DocumentParsedLog>()
            .eq(DocumentParsedLog::getTaskId, taskId).last("LIMIT 1"));
    }

    private DocumentParseFile requireParseFile(Long originalFileId) {
        DocumentParseFile record = documentParseFileMapper.selectOne(new LambdaQueryWrapper<DocumentParseFile>()
            .eq(DocumentParseFile::getDocumentOriginalFileId, originalFileId));
        if (record == null) {
            throw new BusinessException(400, "解析文件记录不存在", 400);
        }
        return record;
    }

    private DocumentOriginalFile getOwnedUploadedFile(Long userId, Long fileId) {
        DocumentOriginalFile file = documentOriginalFileMapper.selectOne(new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, fileId)
            .eq(DocumentOriginalFile::getUserId, userId));
        if (file == null) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        if (!UPLOAD_SUCCESS.equals(file.getUploadStatus()) || !Boolean.TRUE.equals(file.getIsUploadSuccess())) {
            throw new BusinessException(400, "原文件尚未上传成功，不能解析", 400);
        }
        return file;
    }

    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId).eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }
}
