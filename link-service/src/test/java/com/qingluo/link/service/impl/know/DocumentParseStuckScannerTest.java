package com.qingluo.link.service.impl.know;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.service.DocumentParseSseService;
import com.qingluo.link.service.config.ParseResultStuckProperties;
import com.qingluo.link.service.support.ParseResultMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentParseStuckScannerTest {

    @Mock private DocumentParsedLogMapper parsedLogMapper;
    @Mock private DocumentParseFileMapper parseFileMapper;
    @Mock private DocumentParseSseService sseService;
    @Mock private ParseResultMetrics metrics;

    private DocumentParseStuckScanner newScanner(ParseResultStuckProperties properties) {
        return new DocumentParseStuckScanner(parsedLogMapper, parseFileMapper, sseService, properties, metrics);
    }

    private ParseResultStuckProperties defaultProps() {
        ParseResultStuckProperties props = new ParseResultStuckProperties();
        props.setDefaultThreshold(Duration.ofMinutes(5));
        return props;
    }

    @Test
    void Should_RepushFromDb_When_OverThresholdAndDbTerminal() {
        DocumentParsedLog candidate = log(201L, "task-1", 101L, 301L, "created", minutesAgo(6));
        DocumentParsedLog fresh = log(201L, "task-1", 101L, 301L, "success", minutesAgo(6));
        given(parsedLogMapper.selectList(any())).willReturn(List.of(candidate));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 401L, "task-1"));
        given(parsedLogMapper.selectById(201L)).willReturn(fresh);

        newScanner(defaultProps()).scan();

        verify(sseService).publishResultEvent(any());
        verify(metrics).recordRepushed();
        verify(metrics, never()).recordStuck();
        assertNoBusinessWrite();
    }

    @Test
    void Should_AlertOnly_When_OverThresholdAndStillCreated() {
        DocumentParsedLog candidate = log(201L, "task-1", 101L, 301L, "created", minutesAgo(6));
        given(parsedLogMapper.selectList(any())).willReturn(List.of(candidate));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 401L, "task-1"));
        given(parsedLogMapper.selectById(201L)).willReturn(candidate);

        newScanner(defaultProps()).scan();

        verify(sseService, never()).publishResultEvent(any());
        verify(metrics).recordStuck();
        verify(metrics, never()).recordRepushed();
        assertNoBusinessWrite();
    }

    @Test
    void Should_DoNothing_When_NotOverThreshold() {
        // 粗筛返回了一条，但精确阈值（默认 5min）未到（仅 3min）
        DocumentParsedLog candidate = log(201L, "task-1", 101L, 301L, "created", minutesAgo(3));
        given(parsedLogMapper.selectList(any())).willReturn(List.of(candidate));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 401L, "task-1"));

        newScanner(defaultProps()).scan();

        verify(sseService, never()).publishResultEvent(any());
        verify(metrics, never()).recordStuck();
        verify(metrics, never()).recordRepushed();
    }

    @Test
    void Should_Skip_When_CandidateIsNotCurrentTask() {
        DocumentParsedLog candidate = log(201L, "task-old", 101L, 301L, "created", minutesAgo(6));
        given(parsedLogMapper.selectList(any())).willReturn(List.of(candidate));
        // 当前任务指针指向 task-new，候选属于旧任务
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, 401L, "task-new"));

        newScanner(defaultProps()).scan();

        verify(sseService, never()).publishResultEvent(any());
        verify(metrics, never()).recordStuck();
        verify(metrics, never()).recordRepushed();
    }

    @ParameterizedTest
    @CsvSource({
        // datasetId, 数据集阈值分钟(0=不配置回落默认5min), 已用分钟, 是否命中告警
        "300, 0,  6, true",
        "300, 0,  3, false",
        "500, 20, 6, false",
        "500, 20, 25, true"
    })
    void Should_RespectPerDatasetThreshold(long datasetId, int datasetThresholdMin, int elapsedMin, boolean hit) {
        ParseResultStuckProperties props = defaultProps();
        if (datasetThresholdMin > 0) {
            props.setDatasetThresholds(Map.of(datasetId, Duration.ofMinutes(datasetThresholdMin)));
        }
        DocumentParsedLog candidate = log(201L, "task-1", 101L, 301L, "created", minutesAgo(elapsedMin));
        given(parsedLogMapper.selectList(any())).willReturn(List.of(candidate));
        given(parseFileMapper.selectById(301L)).willReturn(parseFile(301L, datasetId, "task-1"));
        if (hit) {
            given(parsedLogMapper.selectById(201L)).willReturn(candidate);
        }

        newScanner(props).scan();

        if (hit) {
            verify(metrics).recordStuck();
        } else {
            verify(metrics, never()).recordStuck();
            verify(sseService, never()).publishResultEvent(any());
        }
    }

    private void assertNoBusinessWrite() {
        verify(parsedLogMapper, never()).updateById(any());
        verify(parseFileMapper, never()).updateById(any());
    }

    private LocalDateTime minutesAgo(int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }

    private DocumentParsedLog log(Long id, String taskId, Long originalFileId, Long parseFileId,
                                   String status, LocalDateTime createdAt) {
        DocumentParsedLog log = new DocumentParsedLog();
        log.setId(id);
        log.setTaskId(taskId);
        log.setDocumentOriginalFileId(originalFileId);
        log.setDocumentParseFileId(parseFileId);
        log.setTaskStatus(status);
        log.setCreatedAt(createdAt);
        return log;
    }

    private DocumentParseFile parseFile(Long id, Long datasetId, String latestTaskId) {
        DocumentParseFile file = new DocumentParseFile();
        file.setId(id);
        file.setDatasetId(datasetId);
        file.setUserId(401L);
        file.setLatestParseTaskId(latestTaskId);
        return file;
    }
}
