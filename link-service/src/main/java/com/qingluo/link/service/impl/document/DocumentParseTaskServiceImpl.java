package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsePipelineMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsePipeline;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.model.dto.response.FileParseResultDTO;
import com.qingluo.link.model.dto.response.FileParseSubmitDTO;
import com.qingluo.link.service.DocumentParseTaskService;
import com.qingluo.link.service.constant.ParsePipelineStatus;
import com.qingluo.link.service.mq.DocumentParseTaskMQ;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 解析任务编排服务。Java 仅管理最新任务指针和消息投递，解析日志与流水线终态由 Python 写入。
 *
 * <p>受理请求时按文件读取最新 document_parsed_log + document_parse_pipeline，分类首次/重试/已成功/运行中：
 * 已成功友好拒绝、失败可重试时复用上次 Markdown 坐标并携带 previous_task_id。终态判定权威源为
 * document_parse_pipeline.pipeline_status（大写），不再读取已删除的 document_parsed_log.task_status。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParseTaskServiceImpl implements DocumentParseTaskService {

    // 以下为对外 DTO / 前端状态词汇（非 DB 列）：终态由 pipeline_status 映射而来。
    public static final String TASK_CREATED = "created";
    public static final String TASK_SUCCESS = "success";
    public static final String TASK_FAILED = "failed";
    public static final String TRIGGER_UPLOAD_AUTO = "upload_auto";
    public static final String TRIGGER_MANUAL_RETRY = "manual_retry";

    private static final String UPLOAD_SUCCESS = "success";
    private static final String MD_BUCKET = "tolink-rag-docs";
    private static final Set<String> PDF_PARSER_BACKENDS = Set.of("auto", "mineru", "opendataloader", "naive");

    private final DatasetMapper datasetMapper;
    private final DatasetParseConfigMapper datasetParseConfigMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentParseFileMapper documentParseFileMapper;
    private final DocumentParsedLogMapper documentParsedLogMapper;
    private final DocumentParsePipelineMapper documentParsePipelineMapper;
    private final ObjectProvider<MQSend> mqSendProvider;

    /** 入口分类结果。RETRY 携带复用旧 Markdown 坐标与上一轮 task_id。 */
    private enum SubmitKind { FIRST, RETRY, REJECT, RUNNING }

    private record RetryContext(String previousTaskId, String mdBucket, String mdObjectKey) {
    }

    private record Classification(SubmitKind kind, RetryContext retry) {
        static Classification of(SubmitKind kind) {
            return new Classification(kind, null);
        }

        static Classification retry(String previousTaskId, String mdBucket, String mdObjectKey) {
            return new Classification(SubmitKind.RETRY, new RetryContext(previousTaskId, mdBucket, mdObjectKey));
        }
    }

    @Override
    @Transactional
    public FileParseSubmitDTO submitManualParse(Long userId, Long fileId) {
        DocumentOriginalFile file = getOwnedUploadedFile(userId, fileId);
        DocumentParseFile parseFile = requireParseFile(fileId);
        Classification classification = classify(parseFile);
        switch (classification.kind()) {
            case RUNNING -> throw new BusinessException(409, "文件正在解析中，请勿重复提交", 409);
            // 已成功（含稀疏向量阶段）的文件友好拒绝，不发 MQ。
            case REJECT -> throw new BusinessException(409, "文件已解析成功，无需重复解析", 409);
            case RETRY -> submit(file, parseFile, TRIGGER_MANUAL_RETRY, classification.retry());
            case FIRST -> submit(file, parseFile, TRIGGER_MANUAL_RETRY, null);
            default -> throw new IllegalStateException("unexpected classification: " + classification.kind());
        }
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
        Classification classification = classify(parseFile);
        // 上传后自动解析恒为首次：运行中跳过去重，已成功跳过避免重复处理，其余一律首次投递。
        if (classification.kind() == SubmitKind.RUNNING) {
            log.info("Skip duplicate automatic parse, fileId={}, latestTaskId={}",
                file.getId(), parseFile.getLatestParseTaskId());
            return;
        }
        if (classification.kind() == SubmitKind.REJECT) {
            log.info("Skip automatic parse for already-succeeded file, fileId={}", file.getId());
            return;
        }
        submit(file, parseFile, TRIGGER_UPLOAD_AUTO, null);
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
        Map<Long, DocumentParsePipeline> currentPipelines = resolveCurrentPipelines(ids, parseFiles);
        List<FileParseResultDTO> result = new ArrayList<>(ids.size());
        for (Long fileId : ids) {
            result.add(buildResultDTO(files.get(fileId), parseFiles.get(fileId),
                currentLogs.get(fileId), currentPipelines.get(fileId)));
        }
        return result;
    }

    /**
     * 入口分类：读"当前任务指针 → 最新 log → 流水线"判定首次/重试/已成功/运行中。
     * 判定走主库（默认数据源即主库），避免主从延迟把刚失败任务误判为首次/已成功。
     */
    private Classification classify(DocumentParseFile parseFile) {
        String latestTaskId = parseFile.getLatestParseTaskId();
        if (!StringUtils.hasText(latestTaskId)) {
            return Classification.of(SubmitKind.FIRST);
        }
        DocumentParsedLog latest = findLogByTaskId(latestTaskId);
        // 指针先于 Python 日志到达：任务在途，视为运行中，避免重复投递覆盖最新任务。
        if (latest == null) {
            return Classification.of(SubmitKind.RUNNING);
        }
        DocumentParsePipeline pipeline = findPipelineByTaskId(latestTaskId);
        // 日志已建但流水线行未建：后处理尚未开始，仍视为运行中。
        if (pipeline == null) {
            return Classification.of(SubmitKind.RUNNING);
        }
        String status = pipeline.getPipelineStatus();
        if (ParsePipelineStatus.isRunning(status)) {
            return Classification.of(SubmitKind.RUNNING);
        }
        if (ParsePipelineStatus.SUCCESS.equals(status)) {
            return Classification.of(SubmitKind.REJECT);
        }
        if (ParsePipelineStatus.FAILED.equals(status)) {
            // 已产出 Markdown 才做阶段恢复重试；未产出（cleaning 前失败）→ 重新首次解析。
            if (StringUtils.hasText(latest.getParsedObjectKey())) {
                return Classification.retry(latestTaskId, latest.getParsedBucketName(), latest.getParsedObjectKey());
            }
            return Classification.of(SubmitKind.FIRST);
        }
        // 未知/异常状态：保守视为运行中，避免误投递。
        return Classification.of(SubmitKind.RUNNING);
    }

    private void submit(DocumentOriginalFile file, DocumentParseFile parseFile, String triggerMode, RetryContext retry) {
        String taskId = UUID.randomUUID().toString();
        documentParseFileMapper.update(null, new LambdaUpdateWrapper<DocumentParseFile>()
            .eq(DocumentParseFile::getId, parseFile.getId())
            .set(DocumentParseFile::getLatestParseTaskId, taskId));
        try {
            MQSend sender = mqSendProvider.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("MQ sender is not configured");
            }
            sender.send(new DocumentParseTaskMQ(buildPayload(taskId, triggerMode, parseFile, file, retry)));
        } catch (RuntimeException e) {
            log.error("Submit parse task failed, fileId={}, taskId={}", file.getId(), taskId, e);
            throw new BusinessException(500, "解析提交失败，请稍后重试", 500);
        }
    }

    private DocumentParseTaskMQ.MsgPayload buildPayload(String taskId, String triggerMode,
                                                          DocumentParseFile parseFile,
                                                          DocumentOriginalFile file,
                                                          RetryContext retry) {
        boolean isRetry = retry != null;
        // 重试复用上一轮 Markdown 坐标；首次解析按新路径生成。
        String mdBucket = isRetry ? retry.mdBucket() : MD_BUCKET;
        String mdObjectKey = isRetry ? retry.mdObjectKey() : buildMdObjectKey(file, taskId);
        String previousTaskId = isRetry ? retry.previousTaskId() : null;
        String pdfParserBackend = resolvePdfParserBackend(file);
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
            mdBucket,
            mdObjectKey,
            pdfParserBackend,
            isRetry,
            previousTaskId
        );
    }

    private String resolvePdfParserBackend(DocumentOriginalFile file) {
        if (!"pdf".equalsIgnoreCase(file.getFileSuffix())) {
            return null;
        }
        DatasetParseConfig config = datasetParseConfigMapper.selectOne(new LambdaQueryWrapper<DatasetParseConfig>()
            .eq(DatasetParseConfig::getUserId, file.getUserId())
            .eq(DatasetParseConfig::getDatasetId, file.getDatasetId())
            .last("LIMIT 1"));
        if (config == null) {
            return null;
        }
        PdfConfig pdfConfig = config.getPdfConfig();
        if (pdfConfig == null || !StringUtils.hasText(pdfConfig.getPdfParserBackend())) {
            return null;
        }
        String backend = pdfConfig.getPdfParserBackend().trim();
        return PDF_PARSER_BACKENDS.contains(backend) ? backend : null;
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

    /** 按各文件当前任务指针加载流水线行，供前端态推导。 */
    private Map<Long, DocumentParsePipeline> resolveCurrentPipelines(List<Long> fileIds,
                                                                     Map<Long, DocumentParseFile> parseFiles) {
        List<String> taskIds = parseFiles.values().stream()
            .map(DocumentParseFile::getLatestParseTaskId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentParsePipeline> byTaskId = documentParsePipelineMapper.selectList(
                new LambdaQueryWrapper<DocumentParsePipeline>()
                    .in(DocumentParsePipeline::getTaskId, taskIds))
            .stream().collect(Collectors.toMap(DocumentParsePipeline::getTaskId, Function.identity(),
                (first, ignored) -> first));
        Map<Long, DocumentParsePipeline> result = new HashMap<>();
        for (Long fileId : fileIds) {
            DocumentParseFile parseFile = parseFiles.get(fileId);
            if (parseFile != null && StringUtils.hasText(parseFile.getLatestParseTaskId())) {
                DocumentParsePipeline pipeline = byTaskId.get(parseFile.getLatestParseTaskId());
                if (pipeline != null) {
                    result.put(fileId, pipeline);
                }
            }
        }
        return result;
    }

    private FileParseResultDTO buildResultDTO(DocumentOriginalFile file, DocumentParseFile parseFile,
                                               DocumentParsedLog log, DocumentParsePipeline pipeline) {
        FileParseResultDTO dto = new FileParseResultDTO();
        dto.setFileId(file.getId());
        dto.setOriginalFilename(file.getOriginalFilename());
        dto.setParsedFilename(log != null && StringUtils.hasText(log.getParsedFilename())
            ? log.getParsedFilename() : toMdFilename(file.getOriginalFilename()));
        String parseStatus = resolveParseStatus(parseFile, pipeline);
        dto.setParseStatus(parseStatus);
        dto.setFailureReason(pipeline != null && ParsePipelineStatus.FAILED.equals(pipeline.getPipelineStatus())
            ? pipeline.getFailureReason() : null);
        dto.setFrontendStatus(frontendStatus(parseStatus));
        return dto;
    }

    /** 由流水线终态（大写）映射为对外 parseStatus 词汇；无流水线行时按当前任务指针推导。 */
    private String resolveParseStatus(DocumentParseFile parseFile, DocumentParsePipeline pipeline) {
        if (pipeline != null) {
            String status = pipeline.getPipelineStatus();
            if (ParsePipelineStatus.SUCCESS.equals(status)) {
                return TASK_SUCCESS;
            }
            if (ParsePipelineStatus.FAILED.equals(status)) {
                return TASK_FAILED;
            }
            // PENDING/PROCESSING/未知 → 进行中
            return TASK_CREATED;
        }
        // 无流水线行：指针已设说明任务刚投递、Python 未及建行 → 进行中；否则从未解析。
        return parseFile != null && StringUtils.hasText(parseFile.getLatestParseTaskId()) ? TASK_CREATED : null;
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

    private DocumentParsePipeline findPipelineByTaskId(String taskId) {
        return documentParsePipelineMapper.selectOne(new LambdaQueryWrapper<DocumentParsePipeline>()
            .eq(DocumentParsePipeline::getTaskId, taskId).last("LIMIT 1"));
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
