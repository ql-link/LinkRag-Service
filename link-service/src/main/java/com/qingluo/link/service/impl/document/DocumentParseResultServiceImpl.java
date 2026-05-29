package com.qingluo.link.service.impl.document;

import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.service.DocumentParseResultService;
import com.qingluo.link.service.DocumentParseSseService;
import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.exception.ParseResultPendingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 校验 Python 已持久化的终态结果，并将其转发给 SSE 订阅端。
 *
 * <p>职责边界（见 docs/parse-result-consumer-resilience）：Python 是权威记账人，
 * 先写 document_parsed_log 的终态再发 parse_result；Java 只做归属校验与 SSE 转发，
 * 从不依据 parse_result 回写任何业务表。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParseResultServiceImpl implements DocumentParseResultService {

    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentParseFileMapper documentParseFileMapper;
    private final DocumentParsedLogMapper documentParsedLogMapper;
    private final DocumentParseSseService documentParseSseService;

    @Override
    public void handleParseResult(DocumentParseResultMQ.MsgPayload payload) {
        DocumentParsedLog logRecord = documentParsedLogMapper.selectById(payload.getDocumentParsedLogId());
        // log 缺失通常是 Python 写库后的跨库可见性 / 主从延迟瞬时态，按可重试处理，
        // 交由错误处理器带退避重试；重试窗口内出现即可正常处理，仍缺失才告警跳过。
        if (logRecord == null) {
            throw new ParseResultPendingException(
                "解析日志暂不存在，待重试，documentParsedLogId=" + payload.getDocumentParsedLogId());
        }
        // 以下三类是消息与已持久化终态的逻辑矛盾，重试不会变对 → 不可恢复，立即告警跳过。
        if (!payload.getTaskId().equals(logRecord.getTaskId())) {
            throw new NonRetryableParseResultException("解析结果消息中的任务标识不匹配");
        }
        if (!payload.getTaskStatus().equals(logRecord.getTaskStatus())) {
            throw new NonRetryableParseResultException("解析结果消息状态与已持久化状态不匹配");
        }
        DocumentParseFile parseFile = documentParseFileMapper.selectById(logRecord.getDocumentParseFileId());
        DocumentOriginalFile originalFile = documentOriginalFileMapper.selectById(payload.getOriginalFileId());
        if (parseFile == null || originalFile == null
            || !payload.getOriginalFileId().equals(logRecord.getDocumentOriginalFileId())
            || !payload.getOriginalFileId().equals(parseFile.getDocumentOriginalFileId())
            || !payload.getOriginalFileId().equals(originalFile.getId())
            || !payload.getDatasetId().equals(parseFile.getDatasetId())
            || !payload.getDatasetId().equals(originalFile.getDatasetId())
            || !payload.getUserId().equals(parseFile.getUserId())
            || !payload.getUserId().equals(originalFile.getUserId())) {
            throw new NonRetryableParseResultException("解析结果消息归属信息不匹配");
        }

        // 当前任务过滤：只为该文件“当前任务”（latest_parse_task_id）推送终态，
        // 避免重试链中旧任务迟到 / 乱序结果向前端误推终态事件。
        String latestTaskId = parseFile.getLatestParseTaskId();
        if (!StringUtils.hasText(latestTaskId)) {
            // 指针缺失（历史数据）无法判定当前任务 → fail-open 放行，并留审计。
            log.warn("Forward parse result with empty latestParseTaskId (fail-open), "
                    + "taskId={}, originalFileId={}, parsedLogId={}, status={}",
                payload.getTaskId(), payload.getOriginalFileId(), payload.getDocumentParsedLogId(),
                payload.getTaskStatus());
        } else if (!latestTaskId.equals(payload.getTaskId())) {
            // 非当前任务（旧任务/乱序）→ 仅审计，不推送终态事件，正常返回即提交跳过。
            log.warn("Skip stale parse result of non-current task, "
                    + "messageTaskId={}, currentTaskId={}, originalFileId={}, parsedLogId={}, status={}",
                payload.getTaskId(), latestTaskId, payload.getOriginalFileId(),
                payload.getDocumentParsedLogId(), payload.getTaskStatus());
            return;
        }

        log.info("Forward parse terminal result, taskId={}, originalFileId={}, parsedLogId={}, status={}",
            payload.getTaskId(), payload.getOriginalFileId(), payload.getDocumentParsedLogId(),
            payload.getTaskStatus());
        documentParseSseService.publishResultEvent(payload);
    }
}
