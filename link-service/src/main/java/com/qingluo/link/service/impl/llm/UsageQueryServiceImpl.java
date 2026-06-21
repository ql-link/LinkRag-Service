package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.model.dto.response.DailyUsageDTO;
import com.qingluo.link.model.dto.response.ModelUsageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UsageLogDTO;
import com.qingluo.link.model.dto.response.UsageSummaryDTO;
import com.qingluo.link.model.dto.response.UsageTrendDTO;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.UsageLog;
import com.qingluo.link.components.mq.constant.UsageStage;
import com.qingluo.link.service.UsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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

    /** 入参日期（yyyy-MM-dd）当天 00:00:00。 */
    private LocalDateTime dayStart(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ISO_DATE).atStartOfDay();
    }

    /** 入参日期（yyyy-MM-dd）当天 23:59:59（含端）。 */
    private LocalDateTime dayEnd(String date) {
        return endOfDay(LocalDate.parse(date, DateTimeFormatter.ISO_DATE));
    }

    /** 某天的 23:59:59（含端）；时间窗口收尾口径的唯一定义点。 */
    private LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59);
    }

    @Override
    /**
     * 汇总指定时间范围内的调用统计数据。
     */
    public UsageSummaryDTO getSummary(Long userId, String startDate, String endDate, String stage) {
        LocalDateTime start = dayStart(startDate);
        LocalDateTime end = dayEnd(endDate);

        List<UsageLog> logs = usageLogMapper.selectList(scopedQuery(userId, start, end, stage));

        if (logs.isEmpty()) {
            return new UsageSummaryDTO(0, 0, 0, 0, 0.0, 0, 0, 0.0);
        }

        long totalCalls = logs.size();
        long totalTokens = logs.stream().mapToLong(UsageLog::getTotalTokens).sum();
        long promptTokens = logs.stream().mapToLong(UsageLog::getPromptTokens).sum();
        long completionTokens = logs.stream().mapToLong(UsageLog::getCompletionTokens).sum();
        long successCalls = logs.stream().filter(this::isSuccess).count();
        long failedCalls = totalCalls - successCalls;
        // 平均延迟仅统计成功调用，避免失败/超时拉偏均值。
        double avgLatency = logs.stream()
            .filter(this::isSuccess)
            .filter(log -> log.getLatencyMs() != null)
            .mapToInt(UsageLog::getLatencyMs)
            .average()
            .orElse(0.0);
        double successRate = totalCalls == 0 ? 0.0 : round4((double) successCalls / totalCalls);

        return new UsageSummaryDTO(totalCalls, totalTokens, promptTokens, completionTokens,
                avgLatency, successCalls, failedCalls, successRate);
    }

    @Override
    /**
     * 按天聚合指定时间范围内的调用数据。
     */
    public List<DailyUsageDTO> getDailyUsage(Long userId, String startDate, String endDate, String stage) {
        LocalDateTime start = dayStart(startDate);
        LocalDateTime end = dayEnd(endDate);

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
        LocalDateTime start = dayStart(startDate);
        LocalDateTime end = dayEnd(endDate);

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

    @Override
    /**
     * 按「厂商 + 模型」SQL 全量聚合，按总 Token 降序。全链路口径（不按 stage 过滤）。
     */
    public List<ModelUsageDTO> getUsageByModel(Long userId, String startDate, String endDate) {
        LocalDateTime start = dayStart(startDate);
        LocalDateTime end = dayEnd(endDate);

        // 注：聚合需 GROUP BY/selectMaps，用裸 QueryWrapper，未走 scopedQuery（全链路口径，不按 stage 过滤）。
        // 若日后给 scopedQuery 增加隔离条件（软删 / 租户等），本方法与 sumTokensAndCalls 的 user/时间过滤需同步。
        QueryWrapper<UsageLog> qw = new QueryWrapper<>();
        qw.select(
                "provider_type AS provider_type",
                "model_name AS model_name",
                "COUNT(*) AS calls",
                "COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens",
                "COALESCE(SUM(completion_tokens), 0) AS completion_tokens",
                "COALESCE(SUM(total_tokens), 0) AS total_tokens")
            .eq("user_id", userId)
            .between("created_at", start, end)
            .groupBy("provider_type", "model_name")
            .orderByDesc("total_tokens");

        List<Map<String, Object>> rows = usageLogMapper.selectMaps(qw);
        return rows.stream()
            .map(row -> new ModelUsageDTO(
                getString(row, "provider_type"),
                getString(row, "model_name"),
                getLong(row, "calls"),
                getLong(row, "prompt_tokens"),
                getLong(row, "completion_tokens"),
                getLong(row, "total_tokens")))
            .toList();
    }

    @Override
    /**
     * 当前周期 vs 等长上一周期的用量环比趋势。全链路口径（不按 stage 过滤）。
     */
    public UsageTrendDTO getUsageTrend(Long userId, String startDate, String endDate) {
        LocalDate startDay = LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE);
        LocalDate endDay = LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE);

        // 上一周期：紧邻当前起点之前、天数相同（含端）。
        long days = Duration.between(startDay.atStartOfDay(), endDay.plusDays(1).atStartOfDay()).toDays();
        LocalDate prevEnd = startDay.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);

        long[] cur = sumTokensAndCalls(userId, startDay.atStartOfDay(), endOfDay(endDay));
        long[] prev = sumTokensAndCalls(userId, prevStart.atStartOfDay(), endOfDay(prevEnd));

        return new UsageTrendDTO(
                cur[0], prev[0], cur[1], prev[1],
                growthRate(cur[0], prev[0]),
                growthRate(cur[1], prev[1]));
    }

    /**
     * 聚合时间窗口内的总 Token 与调用次数。返回 {@code long[]{totalTokens, calls}}。
     */
    private long[] sumTokensAndCalls(Long userId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<UsageLog> qw = new QueryWrapper<>();
        qw.select(
                "COUNT(*) AS calls",
                "COALESCE(SUM(total_tokens), 0) AS total_tokens")
            .eq("user_id", userId)
            .between("created_at", start, end);
        Map<String, Object> row = usageLogMapper.selectMaps(qw).stream().findFirst().orElse(Map.of());
        return new long[]{getLong(row, "total_tokens"), getLong(row, "calls")};
    }

    /** 环比增长率：上一周期为 0（无可比基数）时返回 null。 */
    private Double growthRate(long current, long previous) {
        return previous == 0 ? null : round4((double) (current - previous) / previous);
    }

    private boolean isSuccess(UsageLog log) {
        return "success".equalsIgnoreCase(log.getStatus());
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 从 selectMaps 结果按列名大小写兼容取值（H2 返回大写列名、MySQL 原样）。
     */
    private Object getRaw(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = getRaw(row, key);
        return value == null ? "" : value.toString();
    }

    private long getLong(Map<String, Object> row, String key) {
        Object value = getRaw(row, key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
