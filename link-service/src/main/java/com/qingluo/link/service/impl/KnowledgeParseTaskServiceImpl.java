package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import com.qingluo.link.model.dto.entity.KnowledgeParsedFile;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.service.KnowledgeParseTaskService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.mq.KnowledgeParseTaskMQ;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 文件解析任务服务实现。
 *
 * <p>这里刻意不执行解析，也不写最新解析产物：Java 的职责只到“任务创建 + MQ 投递 + 补偿”。
 * Python 收到 task_id 后负责推进任务状态并写解析产物，避免 Java/Python 双写同一结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseTaskServiceImpl implements KnowledgeParseTaskService {

    public static final String TASK_CREATED = "created";
    public static final String TASK_PROCESSING = "processing";
    public static final String TASK_SUCCESS = "success";
    public static final String TASK_FAILED = "failed";
    public static final String TRIGGER_UPLOAD_AUTO = "upload_auto";
    public static final String TRIGGER_MANUAL_RETRY = "manual_retry";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String RAW_BUCKET = "rag-raw";
    private static final String MD_BUCKET = "rag-md";
    private static final String DISPATCH_FAILED_REASON = "解析任务提交失败，请稍后重试";

    private final DatasetMapper datasetMapper;
    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseTaskMapper knowledgeParseTaskMapper;
    private final KnowledgeParsedFileMapper knowledgeParsedFileMapper;
    private final ObjectProvider<MQSend> mqSendProvider;
    private final KnowledgeFileProperties properties;

    @Override
    public FileParseSubmitDTO submitManualParse(Long userId, Long fileId) {
        KnowledgeOriginalFile file = getOwnedUploadedFile(userId, fileId);
        KnowledgeParseTask task = createTask(file, TRIGGER_MANUAL_RETRY, true);
        dispatchAfterCommit(task, file);
        return toSubmitDTO(file);
    }

    @Override
    @Transactional
    public void submitAutoParseAfterUpload(Long userId, KnowledgeOriginalFile originalFile) {
        if (originalFile == null || originalFile.getId() == null) {
            return;
        }
        KnowledgeOriginalFile file = getOwnedUploadedFile(userId, originalFile.getId());
        KnowledgeParseTask task = createTask(file, TRIGGER_UPLOAD_AUTO, false);
        if (task != null) {
            dispatchAfterCommit(task, file);
        }
    }

    @Override
    public List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(400, "请选择要查看的文件", 400);
        }
        assertOwnedDataset(userId, datasetId);
        List<Long> distinctFileIds = fileIds.stream().distinct().toList();
        List<KnowledgeOriginalFile> files = knowledgeOriginalFileMapper.selectList(
            new LambdaQueryWrapper<KnowledgeOriginalFile>()
                .eq(KnowledgeOriginalFile::getUserId, userId)
                .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
                .in(KnowledgeOriginalFile::getId, distinctFileIds));
        Map<Long, KnowledgeOriginalFile> fileMap = files.stream()
            .collect(Collectors.toMap(KnowledgeOriginalFile::getId, Function.identity()));
        if (fileMap.size() != distinctFileIds.size()) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }

        Map<Long, KnowledgeParseTask> latestTaskMap = latestTaskMap(distinctFileIds);
        Map<Long, KnowledgeParsedFile> parsedFileMap = knowledgeParsedFileMapper.selectList(
                new LambdaQueryWrapper<KnowledgeParsedFile>()
                    .in(KnowledgeParsedFile::getDocumentOriginalFileId, distinctFileIds))
            .stream()
            .collect(Collectors.toMap(KnowledgeParsedFile::getDocumentOriginalFileId, Function.identity()));

        List<FileParseResultDTO> results = new ArrayList<>();
        for (Long fileId : distinctFileIds) {
            KnowledgeOriginalFile file = fileMap.get(fileId);
            KnowledgeParseTask task = latestTaskMap.get(fileId);
            KnowledgeParsedFile parsedFile = parsedFileMap.get(fileId);
            results.add(toResultDTO(file, task, parsedFile));
        }
        return results;
    }

    @Override
    @Transactional
    public int compensateCreatedTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<KnowledgeParseTask> candidates = knowledgeParseTaskMapper.selectList(
            new LambdaQueryWrapper<KnowledgeParseTask>()
                .eq(KnowledgeParseTask::getTaskStatus, TASK_CREATED)
                .lt(KnowledgeParseTask::getDispatchRetryCount, properties.getParseDispatchMaxRetryCount())
                .orderByAsc(KnowledgeParseTask::getUpdatedAt)
                .last("LIMIT 100"));
        int affected = 0;
        for (KnowledgeParseTask task : candidates) {
            if (!shouldRetry(task, now)) {
                continue;
            }
            KnowledgeOriginalFile file = knowledgeOriginalFileMapper.selectById(task.getDocumentOriginalFileId());
            if (file == null || !UPLOAD_SUCCESS.equals(file.getUploadStatus())) {
                markDispatchFailed(task, "原文件不存在或尚未上传成功");
                affected++;
                continue;
            }
            dispatchTask(task, file);
            affected++;
        }
        return affected;
    }

    @Scheduled(fixedDelay = 30_000L)
    public void compensateCreatedTasksOnSchedule() {
        int affected = compensateCreatedTasks();
        if (affected > 0) {
            log.info("Compensated parse task dispatch, affected={}", affected);
        }
    }

    private KnowledgeParseTask createTask(KnowledgeOriginalFile file, String triggerMode, boolean rejectIfRunning) {
        KnowledgeParseTask running = findRunningTask(file.getId());
        if (running != null) {
            if (rejectIfRunning) {
                throw new BusinessException(409, "文件正在解析中，请勿重复提交", 409);
            }
            log.info("Skip auto parse because task is already running, fileId={}, taskId={}",
                file.getId(), running.getTaskId());
            return null;
        }

        KnowledgeParseTask task = new KnowledgeParseTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setDocumentOriginalFileId(file.getId());
        task.setDatasetId(file.getDatasetId());
        task.setUserId(file.getUserId());
        task.setTriggerMode(triggerMode);
        task.setTaskStatus(TASK_CREATED);
        task.setDispatchRetryCount(0);
        knowledgeParseTaskMapper.insert(task);
        return task;
    }

    private void dispatchTask(KnowledgeParseTask task, KnowledgeOriginalFile file) {
        try {
            MQSend mqSend = mqSendProvider.getIfAvailable();
            if (mqSend == null) {
                throw new IllegalStateException("MQSend bean is missing");
            }
            mqSend.send(new KnowledgeParseTaskMQ(buildPayload(task, file)));
            knowledgeParseTaskMapper.update(null, new LambdaUpdateWrapper<KnowledgeParseTask>()
                .eq(KnowledgeParseTask::getId, task.getId())
                .set(KnowledgeParseTask::getLastDispatchedAt, LocalDateTime.now())
                .set(KnowledgeParseTask::getLastDispatchError, null));
        } catch (RuntimeException e) {
            // MQ 投递失败不直接打断前端体验；任务保持 created，后续定时补偿会继续重投。
            int nextRetryCount = safeRetryCount(task) + 1;
            LambdaUpdateWrapper<KnowledgeParseTask> update = new LambdaUpdateWrapper<KnowledgeParseTask>()
                .eq(KnowledgeParseTask::getId, task.getId())
                .set(KnowledgeParseTask::getDispatchRetryCount, nextRetryCount)
                .set(KnowledgeParseTask::getLastDispatchError, abbreviate(e.getMessage()));
            if (nextRetryCount >= properties.getParseDispatchMaxRetryCount()) {
                update.set(KnowledgeParseTask::getTaskStatus, TASK_FAILED)
                    .set(KnowledgeParseTask::getFailureReason, DISPATCH_FAILED_REASON);
            }
            knowledgeParseTaskMapper.update(null, update);
            log.warn("Dispatch parse task MQ failed, taskId={}, retryCount={}",
                task.getTaskId(), nextRetryCount, e);
        }
    }

    private void dispatchAfterCommit(KnowledgeParseTask task, KnowledgeOriginalFile file) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            /*
             * 自动解析通常发生在上传事务内。必须等原文件 success 和解析任务 created 都提交后再投 MQ，
             * 否则 Python 可能先消费消息，却查不到对应任务或原文件成功状态。
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask(task, file);
                }
            });
            return;
        }
        dispatchTask(task, file);
    }

    private KnowledgeParseTaskMQ.MsgPayload buildPayload(KnowledgeParseTask task, KnowledgeOriginalFile file) {
        return new KnowledgeParseTaskMQ.MsgPayload(
            task.getTaskId(),
            file.getId(),
            file.getUserId(),
            file.getDatasetId(),
            file.getFileSuffix(),
            RAW_BUCKET,
            file.getObjectKey(),
            file.getOriginalFilename(),
            MD_BUCKET,
            buildMdObjectKey(file, task.getTaskId()));
    }

    private String buildMdObjectKey(KnowledgeOriginalFile file, String taskId) {
        LocalDate now = LocalDate.now();
        String parsedFilename = toMdFilename(file.getOriginalFilename());
        return "parsed/user-%d/dataset-%d/%04d/%02d/%02d/%s/%s".formatted(
            file.getUserId(), file.getDatasetId(), now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
            taskId, parsedFilename);
    }

    private String toMdFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "parsed.md";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        return baseName + ".md";
    }

    private boolean shouldRetry(KnowledgeParseTask task, LocalDateTime now) {
        if (safeRetryCount(task) >= properties.getParseDispatchMaxRetryCount()) {
            markDispatchFailed(task, DISPATCH_FAILED_REASON);
            return false;
        }
        if (task.getLastDispatchedAt() != null && !StringUtils.hasText(task.getLastDispatchError())) {
            /*
             * created 同时表达“已创建”和“等待 Python 接收”。如果 MQ 已成功投递，就不能再按 created
             * 反复补偿，否则 Python 处理稍慢时会收到同一个 task_id 的重复消息。
             */
            return false;
        }
        LocalDateTime lastDispatchedAt = task.getLastDispatchedAt();
        return lastDispatchedAt == null
            || lastDispatchedAt.plusSeconds(properties.getParseDispatchRetryIntervalSeconds()).isBefore(now);
    }

    private void markDispatchFailed(KnowledgeParseTask task, String reason) {
        knowledgeParseTaskMapper.update(null, new LambdaUpdateWrapper<KnowledgeParseTask>()
            .eq(KnowledgeParseTask::getId, task.getId())
            .set(KnowledgeParseTask::getTaskStatus, TASK_FAILED)
            .set(KnowledgeParseTask::getFailureReason, reason));
    }

    private KnowledgeParseTask findRunningTask(Long fileId) {
        return knowledgeParseTaskMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseTask>()
            .eq(KnowledgeParseTask::getDocumentOriginalFileId, fileId)
            .in(KnowledgeParseTask::getTaskStatus, List.of(TASK_CREATED, TASK_PROCESSING))
            .last("LIMIT 1"));
    }

    private Map<Long, KnowledgeParseTask> latestTaskMap(List<Long> fileIds) {
        List<KnowledgeParseTask> tasks = knowledgeParseTaskMapper.selectList(
            new LambdaQueryWrapper<KnowledgeParseTask>()
                .in(KnowledgeParseTask::getDocumentOriginalFileId, fileIds)
                .orderByDesc(KnowledgeParseTask::getCreatedAt)
                .orderByDesc(KnowledgeParseTask::getId));
        Map<Long, KnowledgeParseTask> result = new HashMap<>();
        for (KnowledgeParseTask task : tasks) {
            result.putIfAbsent(task.getDocumentOriginalFileId(), task);
        }
        return result;
    }

    private FileParseSubmitDTO toSubmitDTO(KnowledgeOriginalFile file) {
        FileParseSubmitDTO dto = new FileParseSubmitDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setFrontendStatus("parse_waiting");
        return dto;
    }

    private FileParseResultDTO toResultDTO(KnowledgeOriginalFile file, KnowledgeParseTask task,
                                           KnowledgeParsedFile parsedFile) {
        FileParseResultDTO dto = new FileParseResultDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setParsedFilename(parsedFile == null ? null : parsedFile.getParsedFilename());
        if (!UPLOAD_SUCCESS.equals(file.getUploadStatus())) {
            dto.setFrontendStatus("upload_failed");
            dto.setFailureReason(file.getFailureReason());
            return dto;
        }
        if (task == null) {
            dto.setFrontendStatus("uploaded");
            return dto;
        }
        dto.setParseStatus(task.getTaskStatus());
        dto.setFailureReason(task.getFailureReason());
        dto.setFrontendStatus(frontendStatus(task.getTaskStatus()));
        return dto;
    }

    private String frontendStatus(String taskStatus) {
        if (TASK_CREATED.equals(taskStatus)) {
            return "parse_waiting";
        }
        if (TASK_PROCESSING.equals(taskStatus)) {
            return "parsing";
        }
        if (TASK_SUCCESS.equals(taskStatus)) {
            return "parse_success";
        }
        if (TASK_FAILED.equals(taskStatus)) {
            return "parse_failed";
        }
        return "uploaded";
    }

    private KnowledgeOriginalFile getOwnedUploadedFile(Long userId, Long fileId) {
        KnowledgeOriginalFile file = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId)
            .eq(KnowledgeOriginalFile::getUserId, userId));
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
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }

    private int safeRetryCount(KnowledgeParseTask task) {
        return task.getDispatchRetryCount() == null ? 0 : task.getDispatchRetryCount();
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return "MQ投递失败";
        }
        String normalized = message.replace("\n", " ").replace("\r", " ");
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }
}
