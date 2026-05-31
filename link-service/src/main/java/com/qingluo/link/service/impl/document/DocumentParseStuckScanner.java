package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsePipelineMapper;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsePipeline;
import com.qingluo.link.service.DocumentParseSseService;
import com.qingluo.link.service.config.ParseResultStuckProperties;
import com.qingluo.link.service.constant.ParsePipelineStatus;
import com.qingluo.link.service.support.ParseResultMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 卡住任务扫描：以 DB 为权威源的通知丢失兜底。
 *
 * <p>职责见 docs/parse-result-consumer-resilience：定时扫描"当前任务仍在运行中
 * （pipeline_status ∈ {PENDING, PROCESSING}）且超过该数据集阈值"的流水线行，重读
 * document_parse_pipeline 取权威终态——</p>
 * <ul>
 *   <li>DB 已终态（SUCCESS/FAILED）：说明终态已落库但 SSE 实时事件可能因消息丢失/消费失败未送达，
 *       以 DB 为准复用 {@link DocumentParseSseService#publishResultEvent} 补推一次（幂等）；</li>
 *   <li>DB 仍运行中：上游（Python）确实卡住或失败未推进，无终态可推，仅告警 + 指标。</li>
 * </ul>
 * <p>全程只读 DB，不回写任何业务状态。原以 document_parsed_log.task_status=created 判定，
 * 该列已迁出，现改判 document_parse_pipeline.pipeline_status。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentParseStuckScanner {

    private final DocumentParsePipelineMapper documentParsePipelineMapper;
    private final DocumentParseFileMapper documentParseFileMapper;
    private final DocumentParseSseService documentParseSseService;
    private final ParseResultStuckProperties properties;
    private final ParseResultMetrics metrics;

    @Value("${spring.profiles.active:default}")
    private String environment;

    @Scheduled(fixedDelayString = "${tolink.parse-result.stuck.scan-interval-ms:60000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        // 粗筛：用所有阈值的最小值先捞出候选，降低扫描量；精确阈值在逐条判定。
        LocalDateTime coarseCutoff = now.minus(properties.minThreshold());
        List<DocumentParsePipeline> candidates = documentParsePipelineMapper.selectList(
            new LambdaQueryWrapper<DocumentParsePipeline>()
                .in(DocumentParsePipeline::getPipelineStatus, ParsePipelineStatus.PENDING, ParsePipelineStatus.PROCESSING)
                .lt(DocumentParsePipeline::getCreatedAt, coarseCutoff));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (DocumentParsePipeline candidate : candidates) {
            // 单条隔离，避免一条异常中断整批扫描。
            try {
                handleCandidate(candidate, now);
            } catch (Exception e) {
                log.warn("Stuck scan failed for one candidate, pipelineId={}, taskId={}",
                    candidate.getId(), candidate.getTaskId(), e);
            }
        }
    }

    private void handleCandidate(DocumentParsePipeline candidate, LocalDateTime now) {
        DocumentParseFile parseFile = documentParseFileMapper.selectById(candidate.getDocumentParseFileId());
        if (parseFile == null) {
            return;
        }
        // 仅处理"当前任务"，旧任务/历史任务不在兜底范围。
        if (!candidate.getTaskId().equals(parseFile.getLatestParseTaskId())) {
            return;
        }
        // 精确阈值：按数据集判定是否真的超时。
        Duration threshold = properties.thresholdOf(parseFile.getDatasetId());
        LocalDateTime createdAt = candidate.getCreatedAt();
        if (createdAt == null || createdAt.isAfter(now.minus(threshold))) {
            return;
        }

        // 重读取权威状态：扫描列表与处理之间可能已转终态。
        DocumentParsePipeline fresh = documentParsePipelineMapper.selectById(candidate.getId());
        if (fresh == null) {
            return;
        }
        long overdueSeconds = Duration.between(createdAt, now).getSeconds();
        String status = fresh.getPipelineStatus();
        if (ParsePipelineStatus.SUCCESS.equals(status) || ParsePipelineStatus.FAILED.equals(status)) {
            // 仍是当前任务才补推，避免与并发重试竞争。
            if (!fresh.getTaskId().equals(parseFile.getLatestParseTaskId())) {
                return;
            }
            documentParseSseService.publishResultEvent(toPayload(fresh, parseFile));
            metrics.recordRepushed();
            log.info("Re-push terminal SSE from DB for stuck task, taskId={}, originalFileId={}, "
                    + "parsedLogId={}, datasetId={}, status={}, overdueSeconds={}",
                fresh.getTaskId(), fresh.getDocumentOriginalFileId(), fresh.getDocumentParsedLogId(),
                parseFile.getDatasetId(), status, overdueSeconds);
        } else {
            // 仍运行中：DB 无终态可推，仅告警 + 指标，等待人工/外部处理。
            metrics.recordStuck();
            log.warn("Stuck parse task detected (still {}), taskId={}, originalFileId={}, "
                    + "parsedLogId={}, datasetId={}, createdAt={}, overdueSeconds={}, env={}",
                status, candidate.getTaskId(), candidate.getDocumentOriginalFileId(), candidate.getDocumentParsedLogId(),
                parseFile.getDatasetId(), createdAt, overdueSeconds, environment);
        }
    }

    private DocumentParseResultMQ.MsgPayload toPayload(DocumentParsePipeline pipeline, DocumentParseFile parseFile) {
        return new DocumentParseResultMQ.MsgPayload(
            pipeline.getTaskId(),
            pipeline.getDocumentOriginalFileId(),
            pipeline.getDocumentParsedLogId(),
            parseFile.getDatasetId(),
            parseFile.getUserId(),
            // 库侧大写终态 → 消息侧小写 task_status。
            ParsePipelineStatus.toMessageStatus(pipeline.getPipelineStatus()),
            pipeline.getFailureReason(),
            pipeline.getFinishedAt() == null ? null : pipeline.getFinishedAt().toString());
    }
}
