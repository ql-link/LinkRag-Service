package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import com.qingluo.link.model.dto.response.LogLabelsDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.AdminLogQueryService;
import com.qingluo.link.service.observability.LogQueryCriteria;
import com.qingluo.link.service.observability.LokiClient;
import com.qingluo.link.service.observability.LokiLogParser;
import com.qingluo.link.service.observability.LokiLogQuery;
import com.qingluo.link.service.observability.LokiLogQueryBuilder;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通过 Java 后端代理 Loki 查询，不向前端暴露 Loki 地址。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogQueryServiceImpl implements AdminLogQueryService {

    private static final List<String> FALLBACK_SERVICES = List.of("tolink-service", "tolink-rag");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final LokiClient lokiClient;
    private final LokiLogQueryBuilder queryBuilder;
    private final LokiLogParser logParser;

    @Override
    public PageResult<LogEntryDTO> queryLogs(String service,
                                             String level,
                                             String traceId,
                                             String keyword,
                                             String startTime,
                                             String endTime,
                                             int page,
                                             int pageSize) {
        LokiLogQuery query = queryBuilder.build(new LogQueryCriteria(
            service, level, traceId, keyword, startTime, endTime, page, pageSize));
        try {
            String responseBody = lokiClient.queryRange(query.getQuery(), query.getStart(), query.getEnd(), query.getLimit());
            List<LogEntryDTO> entries = logParser.parseQueryRange(responseBody);
            int fromIndex = Math.min((query.getPage() - 1) * query.getPageSize(), entries.size());
            int toIndex = Math.min(fromIndex + query.getPageSize(), entries.size());
            return new PageResult<>(entries.subList(fromIndex, toIndex), entries.size(), query.getPage(), query.getPageSize());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw logServiceUnavailable(e);
        } catch (IOException e) {
            throw logServiceUnavailable(e);
        }
    }

    @Override
    public LogLabelsDTO listLabels() {
        List<String> services = FALLBACK_SERVICES;
        try {
            List<String> fromLoki = lokiClient.labelValues("service").stream()
                .filter(value -> value != null && SERVICE_PATTERN.matcher(value).matches())
                .distinct()
                .sorted()
                .toList();
            if (!fromLoki.isEmpty()) {
                services = fromLoki;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("查询 Loki service labels 被中断，使用兜底服务列表", e);
        } catch (Exception e) {
            log.warn("查询 Loki service labels 失败，使用兜底服务列表", e);
        }
        return new LogLabelsDTO(services, LokiLogQueryBuilder.SUPPORTED_LEVELS);
    }

    private BusinessException logServiceUnavailable(Exception e) {
        log.warn("查询 Loki 日志失败", e);
        return new BusinessException(502, "日志服务暂不可用", 502);
    }
}
