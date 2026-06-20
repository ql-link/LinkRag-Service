package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.model.dto.response.DailyUsageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UsageLogDTO;
import com.qingluo.link.model.dto.response.UsageSummaryDTO;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.UsageLog;
import com.qingluo.link.components.mq.constant.UsageStage;
import com.qingluo.link.service.UsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用量查询服务实现
 */
@Service
@RequiredArgsConstructor
public class UsageQueryServiceImpl implements UsageQueryService {

    private final UsageLogMapper usageLogMapper;

    /** 缺省阶段：未指定时仅统计对话生成，保持 LINK-184 前的用量口径。 */
    private static final String DEFAULT_STAGE = UsageStage.CHAT.code();
    /** 全链路标记：查询所有阶段（parse/recall/chat），不加 stage 过滤。 */
    private static final String STAGE_ALL = "all";

    /**
     * 构造按用户 + 时间范围的基础查询，并按 {@code stage} 入参附加阶段过滤：
     * null/空 → 仅 {@link #DEFAULT_STAGE}，{@link #STAGE_ALL} → 不过滤，其余 → 指定阶段。
     */
    private LambdaQueryWrapper<UsageLog> scopedQuery(Long userId, LocalDateTime start, LocalDateTime end, String stage) {
        String effectiveStage = StringUtils.hasText(stage) ? stage.trim() : DEFAULT_STAGE;
        LambdaQueryWrapper<UsageLog> wrapper = new LambdaQueryWrapper<UsageLog>()
                .eq(UsageLog::getUserId, userId)
                .between(UsageLog::getCreatedAt, start, end);
        if (!STAGE_ALL.equalsIgnoreCase(effectiveStage)) {
            wrapper.eq(UsageLog::getStage, effectiveStage);
        }
        return wrapper;
    }

    @Override
    /**
     * 汇总指定时间范围内的调用统计数据。
     */
    public UsageSummaryDTO getSummary(Long userId, String startDate, String endDate, String stage) {
        LocalDateTime start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE).atTime(23, 59, 59);

        List<UsageLog> logs = usageLogMapper.selectList(scopedQuery(userId, start, end, stage));

        if (logs.isEmpty()) {
            return new UsageSummaryDTO(0, 0, 0, 0, 0.0);
        }

        long totalCalls = logs.size();
        long totalTokens = logs.stream().mapToLong(UsageLog::getTotalTokens).sum();
        long promptTokens = logs.stream().mapToLong(UsageLog::getPromptTokens).sum();
        long completionTokens = logs.stream().mapToLong(UsageLog::getCompletionTokens).sum();
        double avgLatency = logs.stream()
            .filter(log -> log.getLatencyMs() != null)
            .mapToInt(UsageLog::getLatencyMs)
            .average()
            .orElse(0.0);

        return new UsageSummaryDTO(totalCalls, totalTokens, promptTokens, completionTokens, avgLatency);
    }

    @Override
    /**
     * 按天聚合指定时间范围内的调用数据。
     */
    public List<DailyUsageDTO> getDailyUsage(Long userId, String startDate, String endDate, String stage) {
        LocalDateTime start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE).atTime(23, 59, 59);

        List<UsageLog> logs = usageLogMapper.selectList(scopedQuery(userId, start, end, stage));

        Map<String, List<UsageLog>> byDate = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCreatedAt().format(DateTimeFormatter.ISO_DATE)
            ));

        return byDate.entrySet().stream()
            .map(entry -> {
                List<UsageLog> dayLogs = entry.getValue();
                return new DailyUsageDTO(
                    entry.getKey(),
                    dayLogs.size(),
                    dayLogs.stream().mapToLong(UsageLog::getPromptTokens).sum(),
                    dayLogs.stream().mapToLong(UsageLog::getCompletionTokens).sum(),
                    dayLogs.stream().mapToLong(UsageLog::getTotalTokens).sum()
                );
            })
            .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
            .toList();
    }

    @Override
    /**
     * 分页查询指定时间范围内的调用日志。
     */
    public PageResult<UsageLogDTO> getUsageLogs(Long userId, String startDate, String endDate, String stage, int page, int pageSize) {
        LocalDateTime start = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE).atTime(23, 59, 59);

        PageHelper.startPage(page, pageSize);
        List<UsageLog> logs = usageLogMapper.selectList(
            scopedQuery(userId, start, end, stage).orderByDesc(UsageLog::getCreatedAt)
        );

        PageInfo<UsageLog> pageInfo = new PageInfo<>(logs);

        List<UsageLogDTO> dtos = logs.stream().map(this::toDTO).toList();

        return new PageResult<>(dtos, pageInfo.getTotal(), page, pageSize);
    }

    /**
     * 将用量日志实体转换为 DTO。
     */
    private UsageLogDTO toDTO(UsageLog log) {
        UsageLogDTO dto = new UsageLogDTO();
        dto.setId(log.getId());
        dto.setConfigId(log.getConfigId());
        dto.setProviderType(log.getProviderType());
        dto.setModelName(log.getModelName());
        dto.setStage(log.getStage());
        dto.setOperation(log.getOperation());
        dto.setPromptTokens(log.getPromptTokens());
        dto.setCompletionTokens(log.getCompletionTokens());
        dto.setTotalTokens(log.getTotalTokens());
        dto.setLatencyMs(log.getLatencyMs());
        dto.setStatus(log.getStatus());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
