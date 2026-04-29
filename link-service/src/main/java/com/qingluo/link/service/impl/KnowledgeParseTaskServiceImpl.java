package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 * 二期文件解析提交流程实现。
 *
 * <p>职责边界按技术文档固定为：
 * 1. Java 校验原文件和解析聚合记录；
 * 2. Java 在同一事务中写入最新 task 指针并同步发送 MQ；
 * 3. MQ 发送失败时，最新 task 指针随事务一起回滚；
 * 4. Python 收到 MQ 后自行创建/推进 document_parse_log，Java 只负责前端查询和事件转发。
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
    private static final String FRONTEND_WAITING = "parse_waiting";
    private static final String FRONTEND_PARSING = "parsing";
    private static final String FRONTEND_SUCCESS = "parse_success";
    private static final String FRONTEND_FAILED = "parse_failed";
    private static final String RAW_BUCKET = "rag-raw";
    private static final String MD_BUCKET = "rag-md";

    private final DatasetMapper datasetMapper;
    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseTaskMapper knowledgeParseTaskMapper;
    private final KnowledgeParsedFileMapper knowledgeParsedFileMapper;
    private final ObjectProvider<MQSend> mqSendProvider;
    @SuppressWarnings("unused")
    private final KnowledgeFileProperties properties;

    @Override
    @Transactional
    public FileParseSubmitDTO submitManualParse(Long userId, Long fileId) {
        KnowledgeOriginalFile file = getOwnedUploadedFile(userId, fileId);
        KnowledgeParsedFile parsedFile = requireParsedFile(file.getId());
        rejectIfRunning(parsedFile, file.getId());

        KnowledgeParseTask task = createTask(file, TRIGGER_MANUAL_RETRY);
        updateLatestTaskPointer(parsedFile.getId(), task.getTaskId());
        sendParseTask(task, parsedFile, file);
        return buildSubmitDTO(file);
    }

    @Override
    @Transactional
    public void submitAutoParseAfterUpload(Long userId, KnowledgeOriginalFile originalFile) {
        if (originalFile == null || originalFile.getId() == null) {
            return;
        }

        KnowledgeOriginalFile file = getOwnedUploadedFile(userId, originalFile.getId());
        KnowledgeParsedFile parsedFile = findParsedFile(file.getId());
        if (parsedFile == null) {
            log.warn("Skip auto parse because parsed file record is missing, fileId={}", file.getId());
            return;
        }

        if (hasRunningTask(parsedFile, file.getId())) {
            log.info("Skip auto parse because task is already running, fileId={}, latestTaskId={}",
                file.getId(), parsedFile.getLatestParseTaskId());
            return;
        }

        KnowledgeParseTask task = createTask(file, TRIGGER_UPLOAD_AUTO);
        updateLatestTaskPointer(parsedFile.getId(), task.getTaskId());
        sendParseTask(task, parsedFile, file);
    }

    @Override
    public List<FileParseResultDTO> listParseResults(Long userId, Long datasetId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(400, "请选择要查看的文件", 400);
        }

        assertOwnedDataset(userId, datasetId);
        List<Long> distinctFileIds = fileIds.stream().distinct().toList();
        Map<Long, KnowledgeOriginalFile> fileMap = loadOwnedFiles(userId, datasetId, distinctFileIds);
        Map<Long, KnowledgeParsedFile> parsedFileMap = loadParsedFiles(distinctFileIds);
        Map<Long, KnowledgeParseTask> currentTaskMap = resolveCurrentTaskMap(distinctFileIds, parsedFileMap);

        List<FileParseResultDTO> results = new ArrayList<>(distinctFileIds.size());
        for (Long fileId : distinctFileIds) {
            results.add(buildResultDTO(fileMap.get(fileId), parsedFileMap.get(fileId), currentTaskMap.get(fileId)));
        }
        return results;
    }

    /**
     * 二期已经废弃 Java 侧补偿重投，这里仅保留空壳调度方法避免历史配置报错。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000L)
    public void compensateCreatedTasksOnSchedule() {
        // no-op in phase 2
    }

    private KnowledgeParseTask createTask(KnowledgeOriginalFile file, String triggerMode) {
        KnowledgeParseTask task = new KnowledgeParseTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setDocumentOriginalFileId(file.getId());
        task.setDatasetId(file.getDatasetId());
        task.setUserId(file.getUserId());
        task.setTriggerMode(triggerMode);
        task.setTaskStatus(TASK_CREATED);
        return task;
    }

    /**
     * 最新 task 指针必须与 MQ 发送处于同一事务边界，否则前端会看到一个无法恢复的假“解析中”状态。
     */
    private void updateLatestTaskPointer(Long parsedFileId, String taskId) {
        UpdateWrapper<KnowledgeParsedFile> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", parsedFileId);
        updateWrapper.set("latest_parse_task_id", taskId);
        knowledgeParsedFileMapper.update(null, updateWrapper);
    }

    private void sendParseTask(KnowledgeParseTask task, KnowledgeParsedFile parsedFile, KnowledgeOriginalFile file) {
        try {
            MQSend mqSend = mqSendProvider.getIfAvailable();
            if (mqSend == null) {
                throw new IllegalStateException("MQSend bean is missing");
            }
            mqSend.send(new KnowledgeParseTaskMQ(buildPayload(task, parsedFile, file)));
        } catch (RuntimeException e) {
            log.error("Submit parse task failed, taskId={}, fileId={}, error={}",
                task.getTaskId(), file.getId(), e.getMessage(), e);
            throw new BusinessException(500, "解析提交失败，请稍后重试", 500);
        }
    }

    private KnowledgeParseTaskMQ.MsgPayload buildPayload(KnowledgeParseTask task,
                                                         KnowledgeParsedFile parsedFile,
                                                         KnowledgeOriginalFile file) {
        return new KnowledgeParseTaskMQ.MsgPayload(
            task.getTaskId(),
            file.getId(),
            parsedFile.getId(),
            file.getUserId(),
            file.getDatasetId(),
            file.getFileSuffix(),
            RAW_BUCKET,
            file.getObjectKey(),
            file.getOriginalFilename(),
            MD_BUCKET,
            buildMdObjectKey(file, task.getTaskId())
        );
    }

    private String buildMdObjectKey(KnowledgeOriginalFile file, String taskId) {
        LocalDate now = LocalDate.now();
        return "parsed/user-%d/dataset-%d/%04d/%02d/%02d/%s/%s".formatted(
            file.getUserId(),
            file.getDatasetId(),
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            taskId,
            toMdFilename(file.getOriginalFilename())
        );
    }

    private String toMdFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "parsed.md";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        return baseName + ".md";
    }

    private Map<Long, KnowledgeOriginalFile> loadOwnedFiles(Long userId, Long datasetId, List<Long> fileIds) {
        List<KnowledgeOriginalFile> files = knowledgeOriginalFileMapper.selectList(
            new LambdaQueryWrapper<KnowledgeOriginalFile>()
                .eq(KnowledgeOriginalFile::getUserId, userId)
                .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
                .in(KnowledgeOriginalFile::getId, fileIds));
        Map<Long, KnowledgeOriginalFile> fileMap = files.stream()
            .collect(Collectors.toMap(KnowledgeOriginalFile::getId, Function.identity()));
        if (fileMap.size() != fileIds.size()) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        return fileMap;
    }

    private Map<Long, KnowledgeParsedFile> loadParsedFiles(List<Long> fileIds) {
        return knowledgeParsedFileMapper.selectList(
                new LambdaQueryWrapper<KnowledgeParsedFile>()
                    .in(KnowledgeParsedFile::getDocumentOriginalFileId, fileIds))
            .stream()
            .collect(Collectors.toMap(KnowledgeParsedFile::getDocumentOriginalFileId, Function.identity()));
    }

    /**
     * 查询结果必须优先遵循 `document_parsed_file.latest_parse_task_id`，
     * 不能只靠时间排序猜“最新任务”，否则刷新或重连时可能展示错任务终态。
     */
    private Map<Long, KnowledgeParseTask> resolveCurrentTaskMap(List<Long> fileIds,
                                                                Map<Long, KnowledgeParsedFile> parsedFileMap) {
        List<KnowledgeParseTask> tasks = knowledgeParseTaskMapper.selectList(
            new LambdaQueryWrapper<KnowledgeParseTask>()
                .in(KnowledgeParseTask::getDocumentOriginalFileId, fileIds)
                .orderByDesc(KnowledgeParseTask::getCreatedAt)
                .orderByDesc(KnowledgeParseTask::getId));

        Map<String, KnowledgeParseTask> taskByTaskId = tasks.stream()
            .filter(task -> StringUtils.hasText(task.getTaskId()))
            .collect(Collectors.toMap(KnowledgeParseTask::getTaskId, Function.identity(), (left, right) -> left));

        Map<Long, KnowledgeParseTask> fallbackLatestTaskByFileId = new HashMap<>();
        for (KnowledgeParseTask task : tasks) {
            fallbackLatestTaskByFileId.putIfAbsent(task.getDocumentOriginalFileId(), task);
        }

        Map<Long, KnowledgeParseTask> result = new HashMap<>();
        for (Long fileId : fileIds) {
            KnowledgeParsedFile parsedFile = parsedFileMap.get(fileId);
            if (parsedFile != null && StringUtils.hasText(parsedFile.getLatestParseTaskId())) {
                KnowledgeParseTask currentTask = taskByTaskId.get(parsedFile.getLatestParseTaskId());
                if (currentTask != null) {
                    result.put(fileId, currentTask);
                    continue;
                }
            }

            KnowledgeParseTask fallbackTask = fallbackLatestTaskByFileId.get(fileId);
            if (fallbackTask != null) {
                result.put(fileId, fallbackTask);
            }
        }
        return result;
    }

    private FileParseSubmitDTO buildSubmitDTO(KnowledgeOriginalFile file) {
        FileParseSubmitDTO dto = new FileParseSubmitDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        // Java 已完成 task 指针更新并同步发出 MQ 后，前端应立即切到解析中，
        // 不再暴露“等待 Python 建日志”的内部中间态。
        dto.setFrontendStatus(FRONTEND_PARSING);
        return dto;
    }

    private FileParseResultDTO buildResultDTO(KnowledgeOriginalFile file,
                                              KnowledgeParsedFile parsedFile,
                                              KnowledgeParseTask currentTask) {
        FileParseResultDTO dto = new FileParseResultDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setParsedFilename(toMdFilename(file.getOriginalFilename()));

        if (!UPLOAD_SUCCESS.equals(file.getUploadStatus())) {
            dto.setFrontendStatus("upload_failed");
            dto.setFailureReason(file.getFailureReason());
            return dto;
        }

        if (currentTask == null) {
            if (parsedFile != null && StringUtils.hasText(parsedFile.getLatestParseTaskId())) {
                dto.setParseStatus(TASK_CREATED);
                dto.setFrontendStatus(FRONTEND_PARSING);
                return dto;
            }
            dto.setFrontendStatus(FRONTEND_WAITING);
            return dto;
        }

        dto.setParseStatus(currentTask.getTaskStatus());
        dto.setFailureReason(currentTask.getFailureReason());
        dto.setFrontendStatus(toFrontendStatus(currentTask.getTaskStatus()));
        return dto;
    }

    private String toFrontendStatus(String taskStatus) {
        if (TASK_CREATED.equals(taskStatus) || TASK_PROCESSING.equals(taskStatus)) {
            return FRONTEND_PARSING;
        }
        if (TASK_SUCCESS.equals(taskStatus)) {
            return FRONTEND_SUCCESS;
        }
        if (TASK_FAILED.equals(taskStatus)) {
            return FRONTEND_FAILED;
        }
        return FRONTEND_WAITING;
    }

    private void rejectIfRunning(KnowledgeParsedFile parsedFile, Long fileId) {
        if (hasRunningTask(parsedFile, fileId)) {
            throw new BusinessException(409, "文件正在解析中，请勿重复提交", 409);
        }
    }

    /**
     * Java 不再预写 document_parse_log，因此“已更新 latest task 指针但 Python 还未建日志”的窗口
     * 也要视为解析中，否则同一文件会在短时间内被重复投递。
     */
    private boolean hasRunningTask(KnowledgeParsedFile parsedFile, Long fileId) {
        if (parsedFile != null && StringUtils.hasText(parsedFile.getLatestParseTaskId())) {
            KnowledgeParseTask latestTask = findTaskByTaskId(parsedFile.getLatestParseTaskId());
            if (latestTask == null) {
                return true;
            }
            if (TASK_CREATED.equals(latestTask.getTaskStatus()) || TASK_PROCESSING.equals(latestTask.getTaskStatus())) {
                return true;
            }
        }
        return findRunningTask(fileId) != null;
    }

    private KnowledgeParseTask findRunningTask(Long fileId) {
        return knowledgeParseTaskMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseTask>()
            .eq(KnowledgeParseTask::getDocumentOriginalFileId, fileId)
            .in(KnowledgeParseTask::getTaskStatus, List.of(TASK_CREATED, TASK_PROCESSING))
            .last("LIMIT 1"));
    }

    private KnowledgeParseTask findTaskByTaskId(String taskId) {
        return knowledgeParseTaskMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseTask>()
            .eq(KnowledgeParseTask::getTaskId, taskId)
            .last("LIMIT 1"));
    }

    private KnowledgeOriginalFile getOwnedUploadedFile(Long userId, Long fileId) {
        KnowledgeOriginalFile file = knowledgeOriginalFileMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeOriginalFile>()
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

    private KnowledgeParsedFile requireParsedFile(Long originalFileId) {
        KnowledgeParsedFile parsedFile = findParsedFile(originalFileId);
        if (parsedFile == null) {
            throw new BusinessException(400, "解析文件记录不存在", 400);
        }
        return parsedFile;
    }

    private KnowledgeParsedFile findParsedFile(Long originalFileId) {
        return knowledgeParsedFileMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeParsedFile>()
                .eq(KnowledgeParsedFile::getDocumentOriginalFileId, originalFileId));
    }

    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }
}
